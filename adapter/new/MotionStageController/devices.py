"""Device-point routing shared by physical and simulated hardware.

Only the control worker polls the card. Vision HTTP is polled by a separate
worker, outside the dispatcher lock, so it cannot stall the motion watchdog.
"""
from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass, field
import math
import threading
import urllib.request
from typing import Any, Callable

from core import AdapterEvent, DeviceData
from southbound import AXIS_STATUS_RUNNING, MotionEngine

ARM_COMMANDS = {'MoveAbsolute', 'Jog', 'Stop', 'ResetMotion', 'SetGripper'}
COMMANDS = {'arm': ARM_COMMANDS, 'valve': {'SetExtDoBit'},
            'corrector': {'SetExtDoBit'}, 'vision': {'VisionCheck'}}
TUBE_COUNT = 4


@dataclass
class Request:
    token: str
    point: str
    message_id: str
    command: str
    due: int = 0
    payload: dict[str, Any] = field(default_factory=dict)
    attempts: int = 0


def integer(value: Any, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f'{name} must be an integer')
    if not math.isfinite(value) or int(value) != value:
        raise ValueError(f'{name} must be an integer')
    return int(value)


class DeviceDispatcher:
    def __init__(self, points: dict[str, dict[str, str]], clock: Any,
                 emit: Callable, publish: Callable, vision_config: dict | None = None,
                 keepalive_ms: int = 1000, io_poll_ms: int = 1000):
        self.points = {p: dict(cfg) for p, cfg in points.items()}
        for point, cfg in self.points.items():
            if cfg.get('kind') not in COMMANDS:
                raise ValueError(f'{point}: missing/invalid device kind')
            if cfg['kind'] in ('valve', 'corrector'):
                bit = int(cfg['bitIndex'])
                if not 1 <= bit <= 9 or int(cfg.get('settleTimeMs', 0)) < 0:
                    raise ValueError(f'{point}: invalid output configuration')
        bits = [int(c['bitIndex']) for c in self.points.values() if c['kind'] in ('valve', 'corrector')]
        if len(bits) != len(set(bits)):
            raise ValueError('Multiple device points cannot own the same output bit')
        if sum(c['kind'] == 'arm' for c in self.points.values()) > 1:
            raise ValueError('One card has only one three-axis arm')
        self.clock, self.emit, self.publish = clock, emit, publish
        self.engine: MotionEngine | None = None
        self.pending: dict[str, Request] = {}
        self.by_token: dict[str, Request] = {}
        self.completed: OrderedDict[tuple[str, str], AdapterEvent] = OrderedDict()
        self._sequence = 0
        self._lock = threading.RLock()
        self._observed: dict[tuple, Any] = {}
        self.snapshots: dict[str, dict] = {}
        self.keepalive_ms = max(1, int(keepalive_ms))
        self._published: dict[str, tuple[int, dict]] = {}
        self.io_poll_ms = max(1, int(io_poll_ms))
        self._outputs: dict[int, int] = {}
        self._next_io_poll = 0
        cfg = vision_config or {}
        self.vision_url = cfg.get('statusUrl', 'http://127.0.0.1:8089/status')
        self.vision_timeout = max(.1, float(cfg.get('requestTimeoutSec', 2)))
        self.vision_retries = max(1, int(cfg.get('maxAttempts', 10)))
        self.vision_interval = max(1, int(cfg.get('retryIntervalMs', 200)))
        self.vision_get: Callable[[], str | None] = self._http_get
        self.vision_online = False
        self._next_vision_probe = 0

    def _http_get(self):
        try:
            with urllib.request.urlopen(self.vision_url, timeout=self.vision_timeout) as response:
                return response.read(4096).decode('utf-8', 'replace')
        except (OSError, ValueError):
            return None

    def bind_engine(self, engine: MotionEngine | None):
        with self._lock:
            if self.engine is not None:
                self.engine.on_event = None
            self.engine = engine
            if engine is not None:
                engine.on_event = self._motion_event
                engine.on_telemetry = None
            self._observed.clear()
            self._published.clear()
            self._outputs.clear()
            self._next_io_poll = 0

    def _event(self, point, name, request=None, **payload):
        event = AdapterEvent(point, name, request.message_id if request else None,
                             payload, self.clock.stamp_ms())
        self.emit(event)
        return event

    def _finish(self, request, success, message, **payload):
        self.by_token.pop(request.token, None)
        if self.pending.get(request.point) is request:
            self.pending.pop(request.point)
        event = self._event(request.point, 'COMMAND_COMPLETED' if success else 'COMMAND_FAILED',
                            request, message=message, **payload)
        key = (request.point, request.message_id)
        self.completed[key] = event
        self.completed.move_to_end(key)
        while len(self.completed) > 1024:
            self.completed.popitem(last=False)

    def _sample(self, point, fields):
        fields = dict(fields)
        self.snapshots[point] = fields
        now = self.clock.mono_ms()
        last = self._published.get(point)
        # 后端逐条入库；每个轮询周期都发相同值会让入库队列越积越长。
        if last is not None and last[1] == fields and now - last[0] < self.keepalive_ms:
            return
        self._published[point] = (now, dict(fields))
        self.publish(DeviceData(point, dict(fields), self.clock.stamp_ms()))

    def execute(self, command):
        with self._lock:
            point, mid, name = command.device_point, str(command.message_id), command.command_name
            cached = self.completed.get((point, mid))
            if cached:
                self.emit(cached)
                return
            active = self.pending.get(point)
            if active and active.message_id == mid:
                return  # MQTT redelivery must not actuate hardware twice.
            self._sequence += 1
            request = Request(str(self._sequence), point, mid, name)
            self._event(point, 'COMMAND_RECEIVED', request)
            cfg = self.points.get(point)
            if not cfg or name not in COMMANDS[cfg['kind']]:
                self._finish(request, False, 'Command is not supported by this device point', errorCode='UNSUPPORTED_COMMAND')
                return
            kind = cfg['kind']
            if active and not (kind == 'arm' and name in ('Stop', 'SetGripper')):
                self._finish(request, False, 'Device point is busy', errorCode='BUSY')
                return
            if kind != 'vision' and (self.engine is None or not self.engine.opened):
                self._finish(request, False, 'Motion card is offline', errorCode='CARD_OFFLINE')
                return
            try:
                params = dict(command.parameters)
                if kind in ('valve', 'corrector'):
                    value = integer(params.get('Value'), 'Value')
                    if value not in (0, 1):
                        raise ValueError('Value must be 0 or 1')
                    bit = int(cfg['bitIndex'])  # Never trust a caller-provided channel.
                    request.payload = {'outputState': value, 'bitIndex': bit}
                    self.pending[point] = request
                    self._event(point, 'COMMAND_RUNNING', request)
                    actual = self._write_output(bit, value)
                    if actual != value:
                        raise OSError('Output readback does not match requested value')
                    self._switch_state(point, actual, kind)
                    if kind == 'corrector':
                        request.due = self.clock.mono_ms() + int(cfg.get('settleTimeMs', 2000))
                    else:
                        self._finish(request, True, 'Output readback confirmed; mechanical position is not measured', **request.payload)
                elif kind == 'vision':
                    tube = integer(params.get('tube'), 'tube')
                    if not 1 <= tube <= TUBE_COUNT:
                        raise ValueError('tube must be between 1 and 4')
                    request.payload = {'tube': tube}
                    self.pending[point] = request
                    self._event(point, 'COMMAND_RUNNING', request)
                else:
                    if name == 'SetGripper':
                        value = integer(params.get('Value'), 'Value')
                        if value not in (0, 1):
                            raise ValueError('Value must be 0 or 1')
                        bit = int(cfg['gripperBit'])
                        self._event(point, 'COMMAND_RUNNING', request)
                        actual = self._write_output(bit, value)
                        if actual != value:
                            raise OSError('Output readback does not match requested value')
                        fields = dict(self.engine.fields())
                        fields['gripperOpen'] = actual == 1
                        self._sample(point, fields)
                        self._gripper_edge(point, actual == 1)
                        self._finish(request, True, 'Gripper output readback confirmed', gripperOpen=actual == 1)
                        return
                    if name in ('MoveAbsolute', 'Jog'):
                        axis = integer(params.get('axis', 1), 'axis')
                        if not 1 <= axis <= 3:
                            raise ValueError('axis must be between 1 and 3')
                        params['axis'] = axis
                    if name == 'MoveAbsolute':
                        params['position'] = integer(params.get('position', 0), 'position')
                    if name == 'Jog':
                        direction = integer(params.get('dir', 1), 'dir')
                        if direction not in (-1, 0, 1):
                            raise ValueError('dir must be -1, 0, or 1')
                    if name in ('MoveAbsolute', 'ResetMotion') and self.engine.busy():
                        self._finish(request, False, 'Axes are busy', errorCode='BUSY')
                        return
                    self.pending[point] = request
                    self.by_token[request.token] = request
                    self.engine.submit(name, request.token, params)
            except ValueError as error:
                self._finish(request, False, str(error), errorCode='INVALID_PARAMETERS')
            except OSError as error:
                self._finish(request, False, str(error), errorCode='SDK_ERROR')

    def _motion_event(self, kind, token, message):
        request = self.by_token.get(token)
        if request is None or kind == 'evt_ack':
            return
        if kind == 'evt_started':
            self._event(request.point, 'COMMAND_RUNNING', request, message=message)
        elif kind in ('evt_success', 'evt_error'):
            success = kind == 'evt_success'
            self._finish(request, success, message, **({} if success else {'errorCode': 'MOTION_FAILED'}))

    def _switch_state(self, point, value, kind):
        is_open = int(value) == 1
        self._sample(point, {'isOpen': is_open})
        key = (point, 'isOpen')
        if self._observed.get(key) == is_open:
            return
        self._observed[key] = is_open
        if kind == 'corrector':
            name = 'CORRECTOR_OPENED' if is_open else 'CORRECTOR_CLOSED'
        else:
            name = 'VALVE_OPENED' if is_open else 'VALVE_CLOSED'
        self._event(point, name, isOpen=is_open)

    def _write_output(self, bit, value):
        self._outputs.pop(bit, None)  # 写入超时后输出实际状态未知，下一周期必须重读。
        self.engine.card.set_ext_do_bit(0, bit, value)
        actual = self.engine.card.get_ext_do_bit(0, bit)
        self._outputs[bit] = actual
        return actual

    def _read_output(self, bit, refresh):
        if refresh or bit not in self._outputs:
            self._outputs[bit] = self.engine.card.get_ext_do_bit(0, bit)
        return self._outputs[bit]

    def has_pending(self) -> bool:
        with self._lock:
            return bool(self.pending)

    def pump(self):
        with self._lock:
            engine = self.engine
            if not engine or not engine.opened:
                return
            engine.pump_once()
            fields, status = engine.polled()
            now = self.clock.mono_ms()
            # 输出位平时按 io_poll_ms 回读；写入时已回读确认，有未完成指令的点仍每周期读。
            refresh = now >= self._next_io_poll
            if refresh:
                self._next_io_poll = now + self.io_poll_ms
            for point, cfg in self.points.items():
                if cfg['kind'] == 'arm':
                    sampled = dict(fields)
                    if 'gripperBit' in cfg:
                        sampled['gripperOpen'] = self._read_output(int(cfg['gripperBit']), refresh) == 1
                    self._sample(point, sampled)
                    if 'gripperOpen' in sampled:
                        self._gripper_edge(point, sampled['gripperOpen'])
                    moving = any(int(word) & AXIS_STATUS_RUNNING for word in status)
                    self._edge(point, 'motion', moving, 'MOVING', 'STOPPED')
                elif cfg['kind'] in ('valve', 'corrector'):
                    request = self.pending.get(point)
                    value = self._read_output(int(cfg['bitIndex']), refresh or request is not None)
                    self._switch_state(point, value, cfg['kind'])
                    if request and self.clock.mono_ms() >= request.due:
                        if value != request.payload['outputState']:
                            self._finish(request, False, 'Output changed during settling', errorCode='OUTPUT_MISMATCH')
                        else:
                            self._finish(request, True, 'Output confirmed and settling wait elapsed; no mechanical feedback', **request.payload)

    def card_failed(self, message):
        with self._lock:
            requests = {r.token: r for r in list(self.pending.values()) + list(self.by_token.values())}
            for request in requests.values():
                if self.points[request.point]['kind'] != 'vision':
                    self._finish(request, False, message, errorCode='CARD_OFFLINE')
            self.bind_engine(None)

    def card_connected(self):
        return

    def _gripper_edge(self, point, is_open):
        key = (point, 'gripperOpen')
        if self._observed.get(key) == is_open:
            return
        self._observed[key] = is_open
        self._event(point, 'GRIPPER_OPENED' if is_open else 'GRIPPER_CLOSED', gripperOpen=is_open)

    def _edge(self, point, key, value, on_name, off_name, **payload):
        token = (point, key)
        if self._observed.get(token) == value:
            return
        self._observed[token] = value
        self._event(point, on_name if value else off_name, **payload)

    @staticmethod
    def parse_vision(body):
        if not isinstance(body, str):
            return None
        text = body.strip()
        parts = text.split('_')
        if len(parts) < 2 or any(part.lower() not in ('true', 'false') for part in parts[:2]):
            return None
        detected = parts[0].lower() == 'true'
        inserted = parts[1].lower() == 'true'
        problems = []
        if not inserted and len(parts) >= 3 and parts[2].strip() not in ('', '0'):
            for token in parts[2].split(','):
                token = token.strip()
                if not token.isdigit():
                    return None
                number = int(token)
                if not 1 <= number <= TUBE_COUNT or number in problems:
                    return None
                problems.append(number)
        return {'labelsComplete': detected, 'allInserted': inserted,
                'problemTubes': problems, 'rawStatus': text}

    def _apply_vision(self, point, result):
        # 第一段为 false 时标签不全，接口要求直接失败，不能判断某根管子是否合上。
        if not result['labelsComplete']:
            return
        fields = dict(self.snapshots.get(point, {}))
        for tube in range(1, TUBE_COUNT + 1):
            fields[f'tube{tube}_closed'] = tube not in result['problemTubes']
        self._sample(point, fields)
        for tube in range(1, TUBE_COUNT + 1):
            if fields[f'tube{tube}_closed']:
                self._edge(point, ('tube', tube), True, f'TUBE{tube}_CLOSED', f'TUBE{tube}_CLOSED', tube=tube)
            else:
                self._observed[(point, ('tube', tube))] = False

    def poll_vision(self):
        # Single dedicated caller. Never hold the card/dispatcher lock over HTTP.
        with self._lock:
            requests = [r for r in self.pending.values() if self.points[r.point]['kind'] == 'vision'
                        and self.clock.mono_ms() >= r.due]
            vision_points = [p for p, c in self.points.items() if c['kind'] == 'vision']
            if not vision_points or (not requests and self.clock.mono_ms() < self._next_vision_probe):
                return
        body = self.vision_get()
        result = self.parse_vision(body)
        with self._lock:
            self.vision_online = result is not None
            self._next_vision_probe = self.clock.mono_ms() + 5000
            if result:
                for point in vision_points:
                    self._apply_vision(point, result)
            for request in requests:
                if self.pending.get(request.point) is not request:
                    continue
                request.attempts += 1
                tube = request.payload['tube']
                closed = bool(result and result['labelsComplete'] and tube not in result['problemTubes'])
                if result and result['labelsComplete'] and closed:
                    self._finish(request, True, 'Tube is closed', tube=tube, closed=True)
                elif result and result['labelsComplete']:
                    self._finish(request, False, 'Tube is not closed', errorCode='TUBE_OPEN', tube=tube, closed=False)
                elif result:
                    self._finish(request, False, 'Labels are missing', errorCode='LABELS_MISSING', tube=tube)
                elif request.attempts >= self.vision_retries:
                    code = 'VISION_UNAVAILABLE' if body is None else 'VISION_INVALID_RESPONSE'
                    self._finish(request, False, 'Vision check did not pass', errorCode=code, tube=tube,
                                 rawStatus=body or '')
                else:
                    request.due = self.clock.mono_ms() + self.vision_interval
