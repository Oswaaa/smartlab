# SmartLab Adapter 设计规范

## 1. 文档用途

本文是 SmartLab Adapter 的标准结构与实现规范，也是后续人工或自动编写
Adapter 的直接输入。

MQTT 信封以仓库内 `协议规范.json` 为准。本文只规定 Adapter 进程怎么拆、
哪些代码焊死、换设备时改哪里。

本文中的“必须”表示所有 Adapter 都应遵守；“可选”表示仅在对应设备需要时实现。

## 2. 改造场景与三模块

实际改造路径是：先有一份能控制设备的程序（例如向 PLC 发令），它还不能和
SmartLab 说话。加上 **core** 之后，它能解析系统指令并对物理点执行，这时已经
是可用 Adapter。若要仿真，core 再分配虚拟点，把打到虚拟点的指令和数据交给
**simulation**；物理点仍走原来的控制程序。

```text
系统 (MQTT)
    ⇅
  core          MQTT、点表、租约、真/假分流（大部分焊死）
    ├─ simulation     假设备（按本设备写）
    └─ control        原控制程序 / 南向（厂家给的）
           ⇅
         真设备
```

没有仿真时，core 永远把命令交给 `control`。有仿真时，虚拟点走 `simulation`，
物理点仍走 `control`。系统从不直接连仿真：它只和某个 `devicePoint` 的
command / telemetry / event 主题通信。

画布走图使用系统进程内模拟器，**不经过**本 Adapter。`TEMPORARY` 实例不会对
Adapter 发 LEASE。

## 3. 标准目录

```text
<adapter-name>/
├── core.py                 # 焊死：MQTT、注册、心跳、点表、租约、分流
├── simulation.py           # 按本设备写：假点如何响应、如何出数
├── control.py              # 原控制程序；也可保留厂家原名，但必须实现 Control 接口
├── adapterSetup.ini        # 发给系统的能力契约；必须遵循已定义的 Adapter Setup
├── runtime.ini             # 本地部署：Broker、设备地址；不随注册上传
└── requirements.txt
```

模块名固定为 **core / simulation / control**。`control.py` 可以改成厂家原文件名
（如 `plcControl.py`），只要入口按本文接口接入 `core.py`。

不要再拆 `northbound.py`。MQTT 与点位/租约同属 core。

**能力契约不得另起格式。** 必须使用本仓库已经定义好的 Adapter Setup：

| 文件 | 角色 |
| --- | --- |
| `Backend/src/main/resources/samples/adapterSetup.ini` | INI 原文规范与示例。每个 Adapter 的 `adapterSetup.ini` 按它改名字、模板和物理点 |
| `Backend/src/main/resources/samples/adapterSetup.json` | 同一契约的 JSON 写法 |
| `Backend/src/main/resources/samples/adapter-config.schema.json` | 上述 JSON/INI 的 schema 与 `x-iniSections` |
| `Backend/src/main/resources/samples/adapter_Parseconfig.json` | 后端解析后的 `parsed_config` 样例，**禁止**当作注册原文 |

注册时把 Adapter 自己的 `adapterSetup.ini`（或等价 JSON）**完整原文**放入
`rawConfigContent`。`rawConfigFormat` 必须与文件一致。

## 4. 什么焊死、什么按设备写

| 部分 | 是否焊死 | 内容 |
| --- | --- | --- |
| `core.py` 的 MQTT、注册、心跳、主题、信封校验 | 是 | 见第 7 节 |
| `core.py` 的物理点表、租约表、LEASE/RELEASE、真/假分流 | 是 | 见第 7 节 |
| `core.py` 里调用 `control` / `simulation` 的那几行 | 接口焊死，实现不在 core | 只调用第 8、9 节接口 |
| `control.py` | 否 | 厂家驱动：给真设备发令、回位置/状态 |
| `simulation.py` | 否 | 假设备：按本模板克隆行为、出仿真数据 |
| `adapterSetup.ini` | 按 Adapter 填写内容，**格式焊死为已定义 Setup** | 只列物理点与模板；不要写 `virtual=true` |
| `runtime.ini` 的 `[smartlab]` / `[runtime]` / `[logging]` | 字段名焊死 | Broker 等 |
| `runtime.ini` 里设备专有段（如 `[plc]`） | 否 | 仅 `control` 读取 |

core **禁止**出现：寄存器地址、Modbus 功能码、厂商 HTTP 路径、DLL、串口帧、
“位置如何积分”一类设备动力学。

## 5. 点位

- **物理点**：写在 `adapterSetup.ini` 的 `[devicePoints.<name>]`。未改造的控制
  程序能管几台设备，就声明几个物理点。`control` 必须按这些点名上报遥测与事件，
  不得把多点读数固定写死到某一个点名（见第 8 节）。
- **虚拟点**：运行时由 LEASE 创建，不写进 `adapterSetup.ini`。命名焊死为
  `{物理点名}_sim_{leaseId}`。同一 `leaseId` 重复 LEASE 必须返回同一虚拟点。
  点名不得含 `/`、`+`、`#`：MQTT 把后两者当通配符，主题层级里出现它们是非法主题。
  该命名同时承担 **MQTT 主题段** 与 **真/假分流** 职责：凡不符合物理点表、且符合
  上述命名约定的点位，core 必须视为虚拟点。
- 系统下发的 `devicePoint` 已经是点位名。core 按名字查表，不做语义映射。
- `heat` → 寄存器、`temperature` → 设备原始字段，属于 `control` 或 `simulation`，
  不属于 MQTT 解析。

一次任务中，相同物理实例只租一台虚拟机；Adapter 侧体现为同一物理点在同一任务
下只有一条生效租约（系统不会对同一物理点重复申请未释放的第二条任务租约）。

## 6. MQTT 主题（焊死）

`{adapterName}`、`{devicePoint}` 来自报文与点表，不得改主题形状。每一段都不得含
`/`、`+`、`#`。

| 主题 | 方向 | 用途 |
| --- | --- | --- |
| `smartlab/adapter/register` | Adapter → 系统 | 注册，`AdapterRegisterRequest` |
| `smartlab/adapter/{adapterName}/heartbeat` | Adapter → 系统 | 心跳，只发 `ALIVE` |
| `smartlab/adapter/{adapterName}/{devicePoint}/command` | 系统 → Adapter | 指令，`CommandMessageFormat` |
| `smartlab/adapter/{adapterName}/{devicePoint}/telemetry` | Adapter → 系统 | 遥测。生产模式走 `formatType: SINGLE`（单点实时），仿真模式走 `formatType: BATCH`（微批次时序打包） |
| `smartlab/adapter/{adapterName}/{devicePoint}/event` | Adapter → 系统 | 事件 |
| `smartlab/adapter/{adapterName}/leaserequest` | 系统 → Adapter | 租约，`LeaseRequestFormat` |
| `smartlab/adapter/{adapterName}/leaseresult` | Adapter → 系统 | 租约结果，`LeaseResultFormat` |

遥测报文采用双轨制契约（`TelemetryMessageFormat`）：
- **生产模式（物理设备采集）**：使用 `formatType: "SINGLE"`（或缺省），上报当前瞬时属性键值对 `telemetryData`；
- **仿真模式（时间压缩推演）**：使用 `formatType: "BATCH"`，在内存中积攒若干个虚拟仿真步长（每个点推进 1 秒虚拟采集时间），通过 `items` 数组单包聚合发出，避免瞬间小包风暴冲垮网络与数据库。

租约不得走 command 主题。不要另发“已收到”确认。系统默认约 15 秒内要等到
`leaseresult`。

LEASE 的 `devicePoint` 是物理点名（克隆来源）。RELEASE 的 `devicePoint` 是虚拟点名
（要拆除的点）。GRANTED / RELEASED 的 `devicePoint` 必须是虚拟点名。LEASE+FAILED
可以没有 `devicePoint`。LEASE 不得回 `RELEASED`，RELEASE 不得回 `GRANTED`。

## 7. `core.py`（焊死）

下面代码就是标准 core。换设备时不要改主题、信封、租约状态机和分流规则；只换
构造时传入的 `control` 与 `simulation`。

完整可运行实现以仓库 `fixture/adapter/reactor/core.py` 为准；本节嵌入的是规范骨架，
下列 **7.1** 中的约束优先于任何局部实现细节。

### 7.1 真/假分流与租约（实现约束）

下列条目属于 core 焊死语义。编写或审查 Adapter 时不得削弱；换设备时也不得在
`control` / `simulation` 里“绕开”这些规则。

**真/假分流**

- 判定虚拟点必须以 **`adapterSetup.ini` 物理点表** 为准：不在表内的 `devicePoint`
  一律视为虚拟点，**必须** 走 `simulation`。
- 进程内租约映射表用于 LEASE/RELEASE、订阅与幂等；**不得** 作为分流的唯一依据。
  租约内存缺失时，仍须按点表与命名约定把命令送进 `simulation`，**禁止** 漏到
  `control`。
- 虚拟点命名 `{物理点}_sim_{leaseId}` 同时是 MQTT 主题段与路由依据；不得擅自
  改后缀规则或使用 topic 非法字符。

**虚拟点生命周期**

- GRANTED 后 core 须完成：`simulation.attach`、虚拟 command 主题订阅、租约记录。
- 虚拟点收到命令前，core 须保证该点已在 `simulation` 挂载；Adapter 重启或内存
  丢失后，须能依据命名约定恢复挂载，而不是等待下一次 LEASE。
- 虚拟点的 internal 参数与 attributeMapping 一律追溯到 LEASE 时的**物理点**，
  不依赖租约表是否仍命中。

**control 边界**

- core **不得** 向 `control` 传递非物理点命令。
- `control` 若仍收到虚拟点（实现错误时的最后防线），须立即 `COMMAND_FAILED`，
  **不得** 启动南向 I/O、轮询或硬件超时验证——那属于物理设备语义，不适用于仿真。

**MQTT 与租约回包**

- 断线重连后须重新订阅 `leaserequest`、全部物理 command、以及**仍生效**的虚拟
  command；租约内存与订阅须一并恢复，否则会出现“租约已成功但命令无响应或走错路径”。
- 每条 RELEASE 都须回 `leaseresult`（`RELEASED` 或 `FAILED` 且带 `errorMessage`）。
  系统以该回包驱动仿真收尾；Adapter 侧不得 silent fail。
- 租约与虚拟点状态主要在 Adapter 进程内存；**不得** 假设重启后系统会自动补发
  LEASE。重连后的订阅恢复与命令分流须自给自足。

**simulation 与 control 对称**

- 两侧对同一命令应发出**同一套** cmd 事件名，并遵守相同的 `messageId` 回传规则。
- 动力学、到位判定、超时与故障注入写在各自模块内；**不得** 让 `control` 的长超时
  或硬件验证逻辑隐式作用于虚拟点。

```python
"""SmartLab Adapter 核心：MQTT、点表、租约、真/假分流。换设备时不要改本文件。"""

from __future__ import annotations

import configparser
import json
import logging
import os
import re
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Protocol

try:
    import paho.mqtt.client as mqtt
except ImportError:  # pragma: no cover
    mqtt = None

REGISTER_TOPIC = "smartlab/adapter/register"


def now_ms() -> int:
    return int(time.time() * 1000)


def topic_segment(value: str, field: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{field} 不能为空")
    if any(ch in text for ch in ("/", "+", "#")):
        raise ValueError(f"{field} 不能包含 MQTT topic 分隔符或通配符")
    return text


def virtual_point_name(physical_point: str, lease_id: int) -> str:
    return topic_segment(f"{physical_point}_sim_{lease_id}", "virtualDevicePoint")


def parse_virtual_point(physical_points: tuple[str, ...] | list[str], virtual_point: str) -> tuple[str, int] | None:
    """从 {物理点}_sim_{leaseId} 解析克隆来源；命名不符合时返回 None。"""
    marker = "_sim_"
    idx = virtual_point.rfind(marker)
    if idx <= 0:
        return None
    physical = virtual_point[:idx]
    if physical not in physical_points:
        return None
    suffix = virtual_point[idx + len(marker):]
    if not suffix.isdigit():
        return None
    return physical, int(suffix)


_BRACKET = re.compile(r"\[((?:\\.|[^\]])*)\]")


def parse_ini_brackets(raw: str) -> list[str]:
    """解析 adapterSetup.ini 中 [DOUBLE][描述][internal=true][sourceField=index] 语法。"""
    text = str(raw or "").strip()
    parts = _BRACKET.findall(text)
    if not parts:
        return [text] if text else []
    return [part.replace(r"\]", "]").replace(r"\\", "\\") for part in parts]


def parse_parameter_value(raw: str) -> dict[str, str]:
    tokens = parse_ini_brackets(raw)
    parsed: dict[str, str] = {}
    if tokens:
        parsed["dataType"] = tokens[0]
    if len(tokens) > 1:
        parsed["description"] = tokens[1]
    for token in tokens[2:]:
        if "=" in token:
            key, value = token.split("=", 1)
            parsed[key.strip()] = value.strip()
    return parsed


@dataclass(frozen=True)
class SystemCommand:
    message_id: str
    device_point: str
    command_name: str
    parameters: dict[str, Any]
    timestamp: int


@dataclass(frozen=True)
class DeviceData:
    device_point: str
    fields: dict[str, Any]
    timestamp: int


@dataclass(frozen=True)
class DeviceBatchData:
    device_point: str
    items: list[dict[str, Any]]
    timestamp: int


@dataclass(frozen=True)
class AdapterEvent:
    device_point: str
    event_name: str
    message_id: str | None
    payload: dict[str, Any]
    timestamp: int


class Control(Protocol):
    """原控制程序。只处理物理点，不知道 MQTT 和租约。"""

    def start(self) -> None: ...
    def stop(self) -> None: ...

    def execute(self, command: SystemCommand) -> None: ...

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None: ...
    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None: ...


class Simulation(Protocol):
    """假设备。只处理已挂载的虚拟点。"""

    def start(self) -> None: ...
    def stop(self) -> None: ...

    def attach(self, virtual_point: str, physical_point: str, state: dict[str, Any] | None = None) -> None: ...
    def detach(self, virtual_point: str) -> None: ...
    def get_device_state(self, virtual_point: str) -> dict[str, float] | None: ...
    def get_all_states(self) -> dict[str, dict[str, float]]: ...

    def execute(self, command: SystemCommand) -> None: ...

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None: ...
    def set_batch_data_handler(self, handler: Callable[[DeviceBatchData], None]) -> None: ...
    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None: ...


@dataclass
class LeaseRecord:
    lease_id: int
    physical_point: str
    virtual_point: str
    released: bool = False
    status: str = "ACTIVE"


@dataclass
class AdapterSetup:
    path: Path
    raw_content: str
    adapter_name: str
    spec_version: str
    raw_config_format: str
    physical_points: tuple[str, ...]
    point_template: dict[str, str]
    attribute_mapping: dict[str, dict[str, str]]
    point_fields: dict[str, dict[str, str]]
    internal_params: dict[tuple[str, str], list[tuple[str, str]]]

    @classmethod
    def load(cls, path: Path) -> "AdapterSetup":
        """只解析已定义的 adapterSetup.ini 契约，不要改 section 层级或方括号语法。"""
        raw = path.read_text(encoding="utf-8-sig")
        parser = configparser.ConfigParser(interpolation=None)
        parser.optionxform = str
        parser.read_string(raw)
        if not parser.has_section("adapter"):
            raise ValueError("adapterSetup.ini 缺少 [adapter]")
        spec_version = parser.get("adapter", "specVersion", fallback="").strip()
        if spec_version != "smartlab.adapter.config.v1":
            raise ValueError("adapter.specVersion 必须是 smartlab.adapter.config.v1")
        name = topic_segment(parser.get("adapter", "adapterName", fallback=""), "adapter.adapterName")
        raw_format = parser.get("adapter", "rawConfigFormat", fallback="").strip().upper()
        if raw_format != "INI":
            raise ValueError("焊死加载器只收 INI；JSON 须先写成已定义的 adapterSetup.ini")
        points: list[str] = []
        templates: dict[str, str] = {}
        mappings: dict[str, dict[str, str]] = {}
        fields: dict[str, dict[str, str]] = {}
        internal_params: dict[tuple[str, str], list[tuple[str, str]]] = {}
        for section in parser.sections():
            parts = section.split(".")
            if len(parts) == 2 and parts[0] == "devicePoints":
                point = topic_segment(parts[1], section)
                points.append(point)
                templates[point] = parser.get(section, "templateName", fallback="").strip()
                extra = {
                    key: parser.get(section, key)
                    for key in parser.options(section)
                    if key not in {"templateName", "description", "devicePoint"}
                }
                fields[point] = extra
            elif len(parts) == 3 and parts[0] == "devicePoints" and parts[2] == "attributeMapping":
                point = topic_segment(parts[1], section)
                mappings[point] = {key: parser.get(section, key) for key in parser.options(section)}
            elif (
                len(parts) == 5
                and parts[0] == "deviceTemplates"
                and parts[2] == "commands"
                and parts[4] == "parameters"
            ):
                template_name, command_name = parts[1], parts[3]
                injections: list[tuple[str, str]] = []
                for key in parser.options(section):
                    parsed = parse_parameter_value(parser.get(section, key))
                    if parsed.get("internal", "").lower() == "true":
                        source_field = parsed.get("sourceField", "").strip()
                        if not source_field:
                            raise ValueError(f"{section}.{key} 的 internal=true 必须提供 sourceField")
                        injections.append((key, source_field))
                if injections:
                    internal_params[(template_name, command_name)] = injections
        if not points:
            raise ValueError("adapterSetup.ini 必须至少声明一个 [devicePoints.<name>]")
        return cls(
            path, raw, name, spec_version, raw_format, tuple(points),
            templates, mappings, fields, internal_params,
        )


@dataclass
class RuntimeConfig:
    broker: str
    port: int
    client_id: str
    username: str
    password: str
    keep_alive_sec: int
    qos: int
    heartbeat_interval_sec: float
    log_level: str
    extra: dict[str, dict[str, str]] = field(default_factory=dict)

    @classmethod
    def load(cls, path: Path) -> "RuntimeConfig":
        parser = configparser.ConfigParser(interpolation=None)
        parser.optionxform = str
        parser.read(path, encoding="utf-8-sig")
        if not parser.has_section("smartlab"):
            raise ValueError("runtime.ini 缺少 [smartlab]")
        smartlab = dict(parser.items("smartlab"))
        runtime = dict(parser.items("runtime")) if parser.has_section("runtime") else {}
        logging_s = dict(parser.items("logging")) if parser.has_section("logging") else {}
        extra = {
            section: dict(parser.items(section))
            for section in parser.sections()
            if section not in {"smartlab", "runtime", "logging"}
        }
        return cls(
            broker=smartlab["broker"].strip(),
            port=int(smartlab["port"]),
            client_id=smartlab["clientId"].strip(),
            username=str(smartlab.get("username", "")).strip(),
            password=str(smartlab.get("password", "")),
            keep_alive_sec=int(smartlab.get("keepAliveSec", "30")),
            qos=int(smartlab.get("qos", "1")),
            heartbeat_interval_sec=float(runtime.get("heartbeatIntervalSec", "10")),
            log_level=str(logging_s.get("level", "INFO")).strip().upper(),
            extra=extra,
        )


class AdapterCore:
    def __init__(
        self,
        setup: AdapterSetup,
        runtime: RuntimeConfig,
        control: Control,
        simulation: Simulation | None = None,
        leases_file: Path | None = None,
    ) -> None:
        self.setup = setup
        self.runtime = runtime
        self.control = control
        self.simulation = simulation
        self.leases_file = leases_file or (setup.path.parent / "virtual_leases.json")
        self._log = logging.getLogger("smartlab.adapter.core")
        self._lock = threading.RLock()
        self._client = None
        self._connected = False
        self._stopping = False
        self._has_registered = False
        self._stop = threading.Event()
        self._leases: dict[int, LeaseRecord] = {}
        self._virtual_to_lease: dict[str, int] = {}
        self._command_topics: dict[str, str] = {
            self._command_topic(point): point for point in setup.physical_points
        }
        self.control.set_data_handler(self._on_backend_data)
        self.control.set_event_handler(self._on_backend_event)
        if self.simulation is not None:
            self.simulation.set_data_handler(self._on_backend_data)
            if hasattr(self.simulation, "set_batch_data_handler"):
                self.simulation.set_batch_data_handler(self._on_backend_batch_data)
            self.simulation.set_event_handler(self._on_backend_event)
        self._load_stored_leases()

    def _load_stored_leases(self) -> None:
        """从本地持久化文件恢复已租约的活跃虚拟点，跳过已释放的历史租约，确保重启后干净自愈。"""
        if not self.leases_file.exists():
            return
        try:
            raw = self.leases_file.read_text(encoding="utf-8")
            if not raw.strip():
                return
            data = json.loads(raw)
            if not isinstance(data, dict):
                return
            with self._lock:
                for key, item in data.items():
                    if not isinstance(item, dict):
                        continue
                    lid = int(item.get("leaseId", key))
                    phys = str(item.get("physicalPoint") or item.get("physical_point") or "").strip()
                    virt = str(item.get("virtualPoint") or item.get("virtual_point") or "").strip()
                    status = str(item.get("status", "ACTIVE")).strip().upper()
                    released = bool(item.get("released", False)) or (status == "RELEASED")
                    state = item.get("state")
                    if released or status != "ACTIVE":
                        self._log.info("跳过已释放的历史虚拟点位: %s (leaseId=%d, status=%s)", virt, lid, status)
                        continue
                    if phys in self.setup.physical_points and virt:
                        rec = LeaseRecord(lid, phys, virt, released=False, status="ACTIVE")
                        self._leases[lid] = rec
                        self._virtual_to_lease[virt] = lid
                        topic = self._command_topic(virt)
                        self._command_topics[topic] = virt
                        if self.simulation is not None:
                            self.simulation.attach(virt, phys, state=state)
                        self._log.info("已从持久化文件恢复活跃租约: leaseId=%s, virtual=%s", lid, virt)
        except Exception as e:
            self._log.warning("读取持久化租约文件失败: %s", e)

    def _save_stored_leases(self) -> None:
        """原子保存当前租约及虚拟设备状态快照到本地文件，记录 status (ACTIVE/RELEASED)。"""
        try:
            snapshot: dict[str, Any] = {}
            with self._lock:
                for lid, rec in self._leases.items():
                    state = None
                    if self.simulation is not None and hasattr(self.simulation, "get_device_state"):
                        state = self.simulation.get_device_state(rec.virtual_point)
                    status = "RELEASED" if (rec.released or rec.status == "RELEASED") else "ACTIVE"
                    snapshot[str(lid)] = {
                        "leaseId": lid,
                        "physicalPoint": rec.physical_point,
                        "virtualPoint": rec.virtual_point,
                        "status": status,
                        "released": (status == "RELEASED"),
                        "state": state,
                        "updatedTime": now_ms(),
                    }
            tmp_path = self.leases_file.with_suffix(".tmp")
            tmp_path.write_text(json.dumps(snapshot, indent=2, ensure_ascii=False), encoding="utf-8")
            tmp_path.replace(self.leases_file)
        except Exception as e:
            self._log.warning("保存持久化租约失败: %s", e)

    def heartbeat_topic(self) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/heartbeat"

    def lease_request_topic(self) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/leaserequest"

    def lease_result_topic(self) -> str:
        return f"smartlab/adapter/{self.setup.adapter_name}/leaseresult"

    def _command_topic(self, device_point: str) -> str:
        point = topic_segment(device_point, "devicePoint")
        return f"smartlab/adapter/{self.setup.adapter_name}/{point}/command"

    def _telemetry_topic(self, device_point: str) -> str:
        point = topic_segment(device_point, "devicePoint")
        return f"smartlab/adapter/{self.setup.adapter_name}/{point}/telemetry"

    def _event_topic(self, device_point: str) -> str:
        point = topic_segment(device_point, "devicePoint")
        return f"smartlab/adapter/{self.setup.adapter_name}/{point}/event"

    def start(self) -> None:
        if mqtt is None:
            raise RuntimeError("缺少 paho-mqtt")
        self.control.start()
        if self.simulation is not None:
            self.simulation.start()
        # 必须使用动态唯一 client_id，防止进程重启或多进程并发时与 Broker 互相踢下线
        unique_client_id = f"{self.runtime.client_id}_{os.getpid()}_{uuid.uuid4().hex[:4]}"
        client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=unique_client_id,
            clean_session=True,
        )
        if self.runtime.username:
            client.username_pw_set(self.runtime.username, self.runtime.password)
        client.reconnect_delay_set(2, 30)
        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        client.on_message = self._on_message
        self._client = client
        client.connect_async(self.runtime.broker, self.runtime.port, keepalive=self.runtime.keep_alive_sec)
        client.loop_start()
        threading.Thread(target=self._heartbeat_loop, name="adapter-heartbeat", daemon=True).start()

    def stop(self) -> None:
        self._stopping = True
        self._stop.set()
        self._save_stored_leases()
        if self._client is not None:
            self._client.loop_stop()
            self._client.disconnect()
        if self.simulation is not None:
            self.simulation.stop()
        self.control.stop()

    def run_forever(self) -> None:
        self.start()
        try:
            while not self._stop.wait(0.5):
                pass
        finally:
            self.stop()

    def _on_connect(self, client, _userdata, _flags, reason_code, _properties=None) -> None:
        # 必须兼容整数 rc 与 ReasonCode 对象，避免连接失败依然误判为成功
        rc = getattr(reason_code, "value", reason_code)
        is_fail = getattr(reason_code, "is_failure", False)
        if rc != 0 or is_fail:
            self._log.error("SmartLab MQTT 连接失败: %s", reason_code)
            self._connected = False
            return
        self._connected = True
        qos = self.runtime.qos
        with self._lock:
            for record in self._leases.values():
                if record.released:
                    continue
                topic = self._command_topic(record.virtual_point)
                self._command_topics[topic] = record.virtual_point
                self._virtual_to_lease[record.virtual_point] = record.lease_id
        client.subscribe(self.lease_request_topic(), qos=qos)
        for topic in self._command_topics:
            client.subscribe(topic, qos=qos)
        # 单层通配符订阅：防御所有当前及未来可能动态派生的虚拟点命令
        wildcard_cmd = f"smartlab/adapter/{self.setup.adapter_name}/+/command"
        client.subscribe(wildcard_cmd, qos=qos)

        # 仅初次上线发注册，重连时不重复广播注册大报文
        if not self._has_registered:
            self._publish_json(REGISTER_TOPIC, {
                "adapterName": self.setup.adapter_name,
                "rawConfigFormat": self.setup.raw_config_format,
                "rawConfigContent": self.setup.raw_content,
                "timestamp": now_ms(),
            })
            self._has_registered = True
        self._publish_json(self.heartbeat_topic(), {"status": "ALIVE", "timestamp": now_ms()})

    def _on_disconnect(self, _client, _userdata, _flags, reason_code, _properties=None) -> None:
        self._connected = False
        if not self._stopping:
            self._log.warning("SmartLab MQTT 断开 rc=%s", reason_code)

    def _on_message(self, _client, _userdata, message) -> None:
        try:
            payload = json.loads(message.payload)
            if not isinstance(payload, dict):
                raise TypeError("payload 必须是对象")
            if message.topic == self.lease_request_topic():
                self._handle_lease(payload)
                return
            point = self._command_topics.get(message.topic)
            if point is None:
                # 尝试从通配符主题 smartlab/adapter/{name}/{point}/command 解析点位
                parts = message.topic.split("/")
                if len(parts) >= 5 and parts[0] == "smartlab" and parts[1] == "adapter" and parts[2] == self.setup.adapter_name and parts[4] == "command":
                    point = parts[3]
                    with self._lock:
                        self._command_topics[message.topic] = point
                else:
                    raise ValueError(f"未知命令主题: {message.topic}")
            self._handle_command(point, payload)
        except Exception:
            self._log.exception("处理 MQTT 消息失败 topic=%s", message.topic)

    def _handle_command(self, topic_point: str, payload: dict[str, Any]) -> None:
        for key in ("messageId", "adapterName", "devicePoint", "commandName", "parameters", "timestamp"):
            if key not in payload:
                raise ValueError(f"命令缺少字段: {key}")
        if payload["adapterName"] != self.setup.adapter_name:
            raise ValueError("adapterName 与本 Adapter 不一致")
        if payload["devicePoint"] != topic_point:
            raise ValueError("devicePoint 与 topic 不一致")
        if not isinstance(payload["parameters"], dict):
            raise TypeError("parameters 必须是对象")
        command = SystemCommand(
            message_id=str(payload["messageId"]),
            device_point=topic_point,
            command_name=str(payload["commandName"]),
            parameters=dict(payload["parameters"]),
            timestamp=int(payload["timestamp"]),
        )
        parameters = dict(command.parameters)
        source_point = self._physical_source(topic_point)
        template_name = self.setup.point_template.get(source_point, "")
        for param_name, source_field in self.setup.internal_params.get((template_name, command.command_name), []):
            if source_field not in self.setup.point_fields.get(source_point, {}):
                raise ValueError(f"设备点 {source_point} 缺少 internal 来源字段: {source_field}")
            parameters[param_name] = self.setup.point_fields[source_point][source_field]
        command = SystemCommand(
            message_id=command.message_id,
            device_point=command.device_point,
            command_name=command.command_name,
            parameters=parameters,
            timestamp=command.timestamp,
        )
        if self._is_virtual(topic_point):
            if self.simulation is None:
                raise RuntimeError("收到虚拟点命令但未装配 simulation")
            self._ensure_virtual_ready(topic_point)
            self.simulation.execute(command)
        else:
            self.control.execute(command)

    def _handle_lease(self, payload: dict[str, Any]) -> None:
        for key in ("leaseId", "adapterName", "action", "devicePoint", "timestamp"):
            if key not in payload:
                raise ValueError(f"租约请求缺少字段: {key}")
        if payload["adapterName"] != self.setup.adapter_name:
            raise ValueError("lease adapterName 与本 Adapter 不一致")
        lease_id = int(payload["leaseId"])
        action = str(payload["action"]).upper()
        device_point = str(payload["devicePoint"]).strip()
        try:
            if action == "LEASE":
                record = self._lease(lease_id, device_point)
                self._publish_lease_result(lease_id, "LEASE", "GRANTED", record.virtual_point)
            elif action == "RELEASE":
                virtual = self._release(lease_id, device_point)
                self._publish_lease_result(lease_id, "RELEASE", "RELEASED", virtual)
            else:
                raise ValueError(f"不支持的租约 action: {action}")
        except Exception as error:
            self._log.exception("租约失败 leaseId=%s action=%s devicePoint=%s", lease_id, action, device_point)
            extra = {"errorMessage": str(error)}
            if action == "LEASE":
                self._publish_lease_result(lease_id, "LEASE", "FAILED", None, extra)
            else:
                self._publish_lease_result(lease_id, "RELEASE", "FAILED", device_point or None, extra)

    def _lease(self, lease_id: int, physical_point: str) -> LeaseRecord:
        if physical_point not in self.setup.physical_points:
            raise ValueError(f"未知物理点: {physical_point}")
        if self.simulation is None:
            raise RuntimeError("未装配 simulation，不能 LEASE")
        with self._lock:
            existing = self._leases.get(lease_id)
            if existing is not None and not existing.released:
                if existing.physical_point != physical_point:
                    raise ValueError("同一 leaseId 不能换物理点")
                return existing
            virtual = virtual_point_name(physical_point, lease_id)
            self.simulation.attach(virtual, physical_point)
            topic = self._command_topic(virtual)
            self._command_topics[topic] = virtual
            if self._client is not None and self._connected:
                self._client.subscribe(topic, qos=self.runtime.qos)
            record = LeaseRecord(lease_id, physical_point, virtual)
            self._leases[lease_id] = record
            self._virtual_to_lease[virtual] = lease_id
            self._save_stored_leases()
            return record

    def _release(self, lease_id: int, virtual_point: str) -> str:
        with self._lock:
            record = self._leases.get(lease_id)
            if record is None:
                if not virtual_point:
                    raise ValueError("RELEASE 找不到租约")
                return virtual_point
            if record.released:
                return record.virtual_point
            if virtual_point and virtual_point != record.virtual_point:
                raise ValueError("RELEASE 的 devicePoint 与租约虚拟点不一致")
            if self.simulation is not None:
                self.simulation.detach(record.virtual_point)
            topic = self._command_topic(record.virtual_point)
            self._command_topics.pop(topic, None)
            self._virtual_to_lease.pop(record.virtual_point, None)
            if self._client is not None and self._connected:
                self._client.unsubscribe(topic)
            record.released = True
            record.status = "RELEASED"
            self._save_stored_leases()
            return record.virtual_point

    def _is_virtual(self, device_point: str) -> bool:
        return device_point not in self.setup.physical_points

    def _ensure_virtual_ready(self, virtual_point: str) -> None:
        if self.simulation is None:
            raise RuntimeError("收到虚拟点命令但未装配 simulation")
        parsed = parse_virtual_point(self.setup.physical_points, virtual_point)
        if parsed is None:
            raise ValueError(f"无法解析虚拟点位: {virtual_point}")
        physical_point, lease_id = parsed
        with self._lock:
            record = self._leases.get(lease_id)
            if record is None or record.released or record.virtual_point != virtual_point:
                record = LeaseRecord(lease_id, physical_point, virtual_point)
                self._leases[lease_id] = record
            self._virtual_to_lease[virtual_point] = lease_id
            topic = self._command_topic(virtual_point)
            self._command_topics[topic] = virtual_point
            if self._client is not None and self._connected:
                self._client.subscribe(topic, qos=self.runtime.qos)
        self.simulation.attach(virtual_point, physical_point)

    def _physical_source(self, device_point: str) -> str:
        if device_point in self.setup.physical_points:
            return device_point
        parsed = parse_virtual_point(self.setup.physical_points, device_point)
        if parsed is not None:
            return parsed[0]
        lease_id = self._virtual_to_lease.get(device_point)
        if lease_id is None:
            raise ValueError(f"未知点位: {device_point}")
        return self._leases[lease_id].physical_point

    def _on_backend_data(self, data: DeviceData) -> None:
        """生产模式（物理设备单点上报，formatType: SINGLE）。"""
        source = self._physical_source(data.device_point)
        mapping = self.setup.attribute_mapping.get(source, {})
        telemetry = {}
        if mapping:
            for attribute, field_name in mapping.items():
                if field_name in data.fields:
                    telemetry[attribute] = data.fields[field_name]
        else:
            telemetry = dict(data.fields)
        self._publish_json(self._telemetry_topic(data.device_point), {
            "formatType": "SINGLE",
            "timestamp": data.timestamp or now_ms(),
            "adapterName": self.setup.adapter_name,
            "devicePoint": data.device_point,
            "telemetryData": telemetry,
        })

    def _on_backend_batch_data(self, batch: DeviceBatchData) -> None:
        """仿真模式（虚拟设备微批次打包上报，formatType: BATCH）。"""
        source = self._physical_source(batch.device_point)
        mapping = self.setup.attribute_mapping.get(source, {})
        mapped_items = []
        for item in batch.items:
            fields = item.get("fields", {})
            telemetry = {}
            if mapping:
                for attribute, field_name in mapping.items():
                    if field_name in fields:
                        telemetry[attribute] = fields[field_name]
            else:
                telemetry = dict(fields)
            mapped_items.append({
                "timestamp": item.get("timestamp", now_ms()),
                "telemetryData": telemetry,
            })
        self._publish_json(self._telemetry_topic(batch.device_point), {
            "formatType": "BATCH",
            "timestamp": batch.timestamp or now_ms(),
            "adapterName": self.setup.adapter_name,
            "devicePoint": batch.device_point,
            "items": mapped_items,
        })

    def _on_backend_event(self, event: AdapterEvent) -> None:
        body: dict[str, Any] = {
            "timestamp": event.timestamp or now_ms(),
            "adapterName": self.setup.adapter_name,
            "devicePoint": event.device_point,
            "eventName": event.event_name,
        }
        if event.message_id:
            body["messageId"] = event.message_id
        if event.payload:
            body["payload"] = event.payload
        self._publish_json(self._event_topic(event.device_point), body)

    def _publish_lease_result(
        self,
        lease_id: int,
        action: str,
        status: str,
        device_point: str | None,
        extra: dict[str, Any] | None = None,
    ) -> None:
        body: dict[str, Any] = {
            "leaseId": lease_id,
            "adapterName": self.setup.adapter_name,
            "action": action,
            "status": status,
            "timestamp": now_ms(),
        }
        if device_point:
            body["devicePoint"] = device_point
        if extra:
            body.update(extra)
        self._publish_json(self.lease_result_topic(), body)

    def _heartbeat_loop(self) -> None:
        interval = max(self.runtime.heartbeat_interval_sec, 1.0)
        while not self._stop.wait(interval):
            if self._connected:
                self._publish_json(self.heartbeat_topic(), {"status": "ALIVE", "timestamp": now_ms()})

    def _publish_json(self, topic: str, payload: dict[str, Any]) -> None:
        client = self._client
        # 必须守卫连接状态，断网期间严禁向 Paho 发送队列狂塞报文导致内存泄漏与雪崩
        if client is None or not self._connected:
            return
        client.publish(topic, json.dumps(payload, ensure_ascii=False), qos=self.runtime.qos, retain=False)


def main() -> int:
    from control import ControlImpl
    from simulation import SimulationImpl

    base = Path(__file__).resolve().parent
    setup = AdapterSetup.load(base / "adapterSetup.ini")
    runtime = RuntimeConfig.load(base / "runtime.ini")
    logging.basicConfig(level=getattr(logging, runtime.log_level, logging.INFO))
    control = ControlImpl(runtime.extra, setup.physical_points, setup.point_fields)
    simulation = SimulationImpl(runtime.extra.get("simulation", {}))
    AdapterCore(setup, runtime, control, simulation).run_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

入口焊死为：加载两个 ini，用 Setup 的物理点表构造 `control`，再与 `simulation`
一起交给 `AdapterCore`。`ControlImpl` / `SimulationImpl` 的类名可按设备修改，
但必须满足第 8、9 节接口。不需要仿真时传入 `simulation=None`，此时 LEASE 必须回
FAILED。

`runtime.extra` 是 `[smartlab]` / `[runtime]` / `[logging]` 以外的段，专供
`control` 读取（例如 `[plc]`）。物理点名单与点字段来自 Setup，**不是**
`runtime.extra`。core 不得解释设备专有段的键。

## 8. `control.py`（按设备写）

这就是原来的驱动程序。没有仿真时，系统命令经 core 拆信封后全部进这里。

必须实现：

```python
class ControlImpl:
    def __init__(
        self,
        extra: dict[str, dict[str, str]],
        physical_points: tuple[str, ...] | list[str],
        point_fields: dict[str, dict[str, str]] | None = None,
    ) -> None:
        """extra 来自 runtime.ini 的设备专有段；physical_points / point_fields 来自 Setup。"""

    def start(self) -> None: ...
    def stop(self) -> None: ...

    def execute(self, command: SystemCommand) -> None:
        """command.device_point 一定是物理点。在这里写成给 PLC/电机的真实调用。"""

    def set_data_handler(self, handler) -> None: ...
    def set_event_handler(self, handler) -> None: ...
```

规则：

- 只接收物理点。不要自己订 SmartLab 主题，不要处理 LEASE。
- 构造时必须拿到 Setup 声明的全部物理点；**不得** 在代码里写死某一个点名。
- **不得** 对虚拟点（含 `_sim_` 命名或任何非物理点表内的点名）执行南向逻辑；
  若 core 误传，须立即 `COMMAND_FAILED`，**不得** 走硬件 I/O 或长时间超时等待。
- `execute` 里把 `command_name` / `parameters` 变成厂家 API 或寄存器写；写操作须
  绑定到该命令的 `device_point`（或多点硬件协议中的等价标识，如 `index`）。
- 设备上报的位置、状态等，组装成 `DeviceData(device_point, fields, timestamp)`
  交给 handler。**`device_point` 必须是产生该读数的实际物理点**，不得把所有点的
  数据都挂到同一个写死的名字上。多点时须能按点表、协议字段（如 `devicePoint` /
  `index`）区分读数；无法归属的读数应丢弃并记日志，不得错点上报。
- `fields` 的键应能被该点 `attributeMapping` 对上（例如映射 `position = absPos`
  则 `fields` 里要有 `absPos`）。
- 指令生命周期用 `AdapterEvent` 上报 `COMMAND_RECEIVED` / `COMMAND_RUNNING` /
  `COMMAND_COMPLETED` / `COMMAND_FAILED` 等；事件上的 `device_point` 与命令一致；
  `message_id` 必须回传对应命令的 `messageId`。
- 不要把虚拟点写进本模块。core 不会把虚拟点命令交给 `control`；若收到，说明分流或
  core 实现有误，应快速失败而不是按真设备处理。

## 9. `simulation.py`（按设备写）

仿真是多出来的那台假设备。core 在 GRANTED 之后调用 `attach`，之后该虚拟点上的
命令和数据都像真设备一样进出 core。

必须实现的标准接口：

```python
class SimulationImpl:
    def __init__(self, config: dict[str, Any] | None = None) -> None:
        """从 runtime.ini 的 [simulation] 段读取 batchSize, intervalSec, simStepMs 等配置。"""

    def start(self) -> None: ...
    def stop(self) -> None: ...

    def attach(self, virtual_point: str, physical_point: str, state: dict[str, Any] | None = None) -> None:
        """按物理点的模板克隆一台假设备，挂到 virtual_point。若提供 state 则恢复状态快照。"""

    def detach(self, virtual_point: str) -> None:
        """拆除假设备。重复 detach 视为已拆除。"""

    def get_device_state(self, virtual_point: str) -> dict[str, float] | None:
        """导出单个虚拟设备的状态快照（用于本地持久化）。"""

    def get_all_states(self) -> dict[str, dict[str, float]]:
        """导出所有活跃虚拟设备的状态快照。"""

    def execute(self, command: SystemCommand) -> None:
        """command.device_point 一定是虚拟点。不要发到真 PLC。"""

    def set_data_handler(self, handler: Callable[[DeviceData], None]) -> None: ...
    def set_batch_data_handler(self, handler: Callable[[DeviceBatchData], None]) -> None: ...
    def set_event_handler(self, handler: Callable[[AdapterEvent], None]) -> None: ...
```

规则：

- 假设备往 handler 送的数据字段必须能对齐 Setup 中声明的物理点属性映射（例如 `raw_temp`, `raw_press`）。
- 同一 `leaseId` 的 `attach` 由 core 保证幂等；`simulation` 对已存在的
  `virtual_point` 应保持原实例，不要另造一台。
- core 依据本地持久化记录在重启时调用带 `state` 的 `attach`；`simulation` 须支持
  状态平滑恢复，避免重启后虚拟物理量瞬间复位为初始值。
- 动力学、到位时间、故障注入全部写在本文件，不要写进 `core.py`。
- 指令事件信封与 `control` 相同，只是 `device_point` 为虚拟名；**不得** 沿用
  `control` 的真设备超时/硬件验证语义。

### 9.1 仿真模块的可复用架构与业务隔离（规范方法论）

为了后续根据此规范快速生成任意设备的 Adapter，仿真模块必须严格区分**通用框架逻辑（可复用）**与**专有动力学逻辑（设备定制）**：

#### 1. 可沉淀复用的框架层（各 Adapter 100% 通用）
- **`VirtualDeviceRegistry`（虚拟设备容器）**：统一维护 `_virtual_devices: dict[str, Any]`，处理线程锁、`attach` 幂等挂载、`detach` 资源释放，以及批量快照导出。
- **`VirtualClock`（虚拟仿真时钟）**：维护单调递增的虚拟时间戳 `sim_timestamp`。仿真步长推进时递增虚拟秒（如 `sim_timestamp += 1000`），绝不能把几百毫秒内生成的多个计算步长都打上相同的真实服务器时间。
- **`MicroBatchAccumulator`（微批次汇聚与推流器）**：负责在一个推流循环周期内，把单步迭代出来的物理量列表暂存，聚合为 `DeviceBatchData` 一包发出，避免重复编写消息组装代码。
- **`AsyncCommandRunner`（指令生命周期执行模板）**：收到指令后在独立线程中执行，先发射 `COMMAND_RUNNING`，物理量逼近达到条件后发射 `COMMAND_SUCCESS`，参数非法时发射 `COMMAND_FAILED`，保证 `messageId` 原样回传。

#### 2. 设备定制的业务层（按硬件物理特性编写）
- **动力学物理演化算法**：例如反应釜温度根据热传导和加热功率逐渐逼近目标温度；机械臂六轴坐标插值；泵阀流量阻力计算等。
- **设备物理量字段**：如反应釜的 `raw_temp` / `raw_press`，机械臂的 `x` / `y` / `z` / `speed` 等。

---

### 9.2 微批次打包与时间压缩规范（频率、容量与压缩比）

针对仿真模式中“时间压缩推演”（例如把几天的真实实验在几分钟内跑完）生成的大量数据点，必须遵循以下微批次规约：

#### 1. 推流时间周期（Batch Interval，网络发送间隔）
- **规范推荐值**：**`200ms ~ 1000ms`**（工程默认推荐 **`500ms ~ 1000ms`**）。
- **约束边界**：
  - **下限不得低于 100ms**：否则退化为高频网络碎包轰炸，浪费 MQTT 与 TCP 头部开销。
  - **上限不得高于 2000ms**：否则前端孪生看板与实时曲线会有明显的顿挫感。

#### 2. 单包数据点容量（Batch Size，批次点数）
- **规范推荐区间**：**`10 ~ 100 个数据点 / 单包`**（绝对安全上限为 200 个点）。
- **约束理由**：单个包含 50 个点的 JSON 报文约 5KB~10KB，MQTT Broker 处理吞吐最高；后端数据库单次执行一条 50~100 行的批量 INSERT 耗时仅约 10~20ms，能从根本上消除数据库写入等待导致的 MQTT 回调线程堵塞。

#### 3. 时间压缩倍率与步长标准计算公式
- **单步虚拟时间增量**：$\Delta T_{\text{sim}}$ 默认为 **`1000ms`（虚拟 1 秒）**。
- **时间压缩倍率**：
  $$K = \frac{\text{单批次覆盖的虚拟时间}}{\text{现实发送间隔}} = \frac{N \times \Delta T_{\text{sim}}}{T_{\text{interval}}}$$
  其中 $N$ 为单批包含的数据点数，$T_{\text{interval}}$ 为推流周期。
- **标准场景参考配置**：
  | 仿真模式场景 | 发送周期 $T_{\text{interval}}$ | 单批点数 $N$ | 虚拟步长 $\Delta T_{\text{sim}}$ | 压缩加速比 $K$ | 适用业务 |
  | :--- | :--- | :--- | :--- | :--- | :--- |
  | **平稳推演（默认）** | 1.0 秒 | 5 个点 | 1 秒/点 | **5 倍速** | 日常流程校验、设备控制联调 |
  | **中速加速推演** | 0.5 秒 | 20 个点 | 1 秒/点 | **40 倍速** | 半小时内的实验流程压缩到数十秒 |
  | **高速压缩推演** | 0.5 秒 | 50 个点 | 1 秒/点 | **100 倍速** | 数天的长周期培养/反应压缩至半小时 |
  | **极限压缩上限** | 1.0 秒 | 100 个点 | 1 秒/点 | **100 倍速** | 大批量数据归档推演的安全边界 |

---

### 9.3 指令生命周期响应与防看门狗熔断时序（关键规约）

系统状态机（`StateMachineEngine`）对每一条下发的指令维护严格的状态流转：
$$\text{IDLE} \xrightarrow{\text{系统下发}} \text{SENT} \xrightarrow{\text{Adapter 回复 COMMAND\_RUNNING}} \text{RUNNING} \xrightarrow{\text{达成目标/完成}} \text{COMPLETED}$$

#### 1. 10 秒看门狗熔断机制
- 系统内部包含定时扫描看门狗（Watchdog），时限默认 **10 秒**。
- **看门狗只检测处于 `SENT` 状态等待认领的指令**：若指令下发 10 秒后仍未收到 Adapter 上报任何推进状态的事件（尤其是 `COMMAND_RUNNING`），系统判定 Adapter 或设备挂死，强制将指令判定为 `FAILED` 并触发报警和节点中断。
- 一旦 Adapter 上报 `COMMAND_RUNNING`，状态机立即步入 `RUNNING` 阶段，10 秒看门狗自动解除监控，设备即可安全地进行长周期的实际动作或动力学仿真。

#### 2. Adapter 执行体的响应规约（真机与仿真机 100% 遵从）
- **毫秒级回执原则**：无论真机控制（`control`）还是虚拟推演（`simulation`），在 `execute(command)` 入口处完成参数解析、将指令写入发送队列或启动仿真协程后，**必须立即（毫秒级）调用 `event_handler` 发射 `COMMAND_RUNNING`**。
- **禁止在发射 `COMMAND_RUNNING` 前进行阻塞式 I/O**：
  - `control.py` 禁止在硬件握手成功或等待到位前 sleep；应把写入任务提交给后台 I/O 线程，立刻发出 `COMMAND_RUNNING`。
  - `simulation.py` 禁止在 `time.sleep` 或步长推演循环结束前才上报 `COMMAND_RUNNING`；必须先发 `COMMAND_RUNNING`，再进入到位等待线程。

---

### 9.4 双模时钟与物理闭环完成规范（杜绝假等待与时间逃逸）

在数字孪生与仿真推演中，常见两个严重破坏业务体验的反模式：
1. **时钟逃逸**：待机（IDLE）时仍在无休止按几十倍速自增虚拟时间戳，导致空闲几分钟后时间戳飞到未来数小时甚至数天，破坏前端实时看板与时序数据库连续性；
2. **假等待脱节**：推演算法在 1 秒内已经把物理量算到了目标值，但指令完成回调却在 `time.sleep(duration)` 里死等 20 秒现实时间，导致“数据已到位、指令却迟迟不完成”的脱节怪象。

为彻底根除上述缺陷，`simulation.py` 必须实现**双模态时钟与闭环判定机制**：

#### 1. 空闲态（IDLE）：现实同步时钟
- **稳态推流**：当无指令在跑时，设备处于常态待机（如室温 25℃，常压 0.1MPa）。
- **同步频率**：以每 1.0 秒 1 次的平稳频率上报单点读数（或单点批次），叠加轻微物理白噪声。
- **时钟锚定**：时间戳与现实时钟 `now_ms()` 平稳同步（$\text{sim\_timestamp} = \max(\text{now\_ms}(), \text{sim\_timestamp} + 1000)$），保证时间戳单调递增且绝不狂飙超前。

#### 2. 工作态（RUNNING）：时间压缩推演
- **激活时机**：收到控制指令（如 `heat`、`pressurize`）后进入推演态。
- **加速推演**：从当前时间戳开始，按 `batchSize`、`intervalSec`、`simStepMs` 离散步进推进，微批次打包上传（`formatType: BATCH`）。

#### 3. 物理量到位闭环触发（严禁使用 `time.sleep` 假等待）
- **步数离散化映射**：真实的持续时间参数（如 `durationSec` = 20）映射为离散动力学总步数：
  $$S_{\text{total}} = \max\left(1, \text{round}\left(\frac{\text{durationSec} \times 1000}{\Delta T_{\text{sim}}}\right)\right)$$
- **闭环发射**：推演循环每前进一步，计算当前物理量；一旦步数走满（$S_{\text{current}} \ge S_{\text{total}}$）或物理量精确达到设定目标：
  1. 立即发射 `TARGET_xxx_REACHED` 事件；
  2. 立即以对应的 `messageId` 发射 `COMMAND_COMPLETED` 事件；
  3. 将虚拟设备置回空闲态（IDLE）。
- **协同效果**：在 40 倍速下，20 秒的升温过程在现实世界中刚好约 0.5 秒完成，推流曲线到位与工作流节点完成在毫秒级完美协同！

---

## 10. 配置

### 10.1 Adapter Setup（必须用已定义契约）

发给系统的能力契约**就是**本目录已经定好的 Setup，不要在 Adapter 里另写一套
section 或 JSON 形状。

| 路径 | 用法 |
| --- | --- |
| `samples/adapterSetup.ini` | INI 规范 + 完整示例。新 Adapter 复制它，只改 `adapterName`、模板、命令、物理点 |
| `samples/adapterSetup.json` | 同一内容的 JSON |
| `samples/adapter-config.schema.json` | 字段、必填项、INI section 对照 |
| `samples/adapter_Parseconfig.json` | 系统解析后的内部结构。Adapter **不得**把这份当作 `rawConfigContent` |

焊死约定：

- `specVersion` 必须是 `smartlab.adapter.config.v1`。
- INI 用 `[deviceTemplates.<template>]`、`[devicePoints.<point>]`、
  `[....commands.<command>.parameters]` 以及 `[数据类型][描述][internal=…][sourceField=…]`。
- `internal=true` 必须带 `sourceField`，core 从对应 `[devicePoints.<point>]` 取值注入。
- 只声明**物理点**。不要写 `virtual=true`，不要为仿真预留点位。
- 禁止把密码、Broker、设备 IP 写进 Setup。
- 后端真正解析的是 `AdapterManifestService`。改契约必须同时改 schema、INI/JSON
  示例、Java 解析器和本文，不得只改 Adapter。

注册报文外层：

```json
{
  "adapterName": "与 [adapter] adapterName 完全一致",
  "rawConfigFormat": "INI",
  "rawConfigContent": "<本 Adapter 的 adapterSetup.ini 完整原文>",
  "timestamp": 1719892800
}
```

`rawConfigContent` 必须是 flat 的 `deviceTemplates` + `devicePoints`（与
`adapterSetup.ini` / `adapterSetup.json` 相同）。带 `deviceCategories`、
`registerMeta` 的对象是 `parsed_config`，那是系统存的，不是 Adapter 提交的。

### 10.2 `runtime.ini`

不发送给 SmartLab。

```ini
[smartlab]
broker = 127.0.0.1
port = 1883
username = adapter
password = 123456
clientId = smartlab_plc_adapter
keepAliveSec = 30
qos = 1

[runtime]
heartbeatIntervalSec = 10

[logging]
level = INFO

[simulation]
; 每次发送打包的点数（默认 5，推荐 10~100）
batchSize = 20
; 发送周期（秒，默认 1.0，推荐 0.2~1.0）
intervalSec = 0.5
; 每个数据点对应的虚拟推演步长（毫秒，默认 1000ms=1秒；长周期实验可放大为 5000 或 30000 降低高频数据量）
simStepMs = 1000

; 以下段名自定，仅 control 读取
[plc]
; broker / 串口 / 点位地址等
```

密码优先环境变量或密钥文件，不要把生产口令提交进仓库。

---

## 11. 常见工程陷阱与故障排查（Gotchas）

在开发与联调 Adapter 过程中，以下 5 个陷阱极其容易导致“高频断连”、“静默丢失命令”或“雪崩崩溃”，所有新 Adapter 编写时必须严格规避：

### 1. ClientId 冲突导致 Broker 互踢风暴
* **现象**：Adapter 频繁在连上 Broker 数秒内被强制断开（`rc=7` 或 `Socket error`），Broker 日志中出现大量的客户端重复连接与踢掉旧连接记录。
* **原因**：多个进程或重启后的进程复用了 `runtime.ini` 中相同的静态 `clientId`，Broker 按照 MQTT 标准把同名旧客户端踢下线。
* **规范**：`core.py` 中初始化 `mqtt.Client` 时，**必须动态拼接 PID 和随机后缀**：
  `f"{self.runtime.client_id}_{os.getpid()}_{uuid.uuid4().hex[:4]}"`。

### 2. `on_connect` 返回码解析陷阱（Paho v1 与 v2 差异）
* **现象**：当 Broker 发生鉴权失败或网络拒绝时，Adapter 依然打印 `已连接` 并开始发送数据，随后迅速异常崩溃。
* **原因**：Paho MQTT Python 库在 v1 和 v2 中传递的 `reason_code` 类型不同（一个是整数 `0`，一个是对象 `ReasonCode`）。
* **规范**：判断连接状态必须严格按如下方式兼容检查：
  ```python
  rc = getattr(reason_code, "value", reason_code)
  is_fail = getattr(reason_code, "is_failure", False)
  if rc != 0 or is_fail:
      self._connected = False
      return
  ```

### 3. 断网期间发送队列雪崩与内存溢出
* **现象**：在 MQTT 发生临时网络抖动或 Broker 重启时，遥测和心跳线程仍在无休止发包，导致重连瞬间产生数千条未决报文挤占 Socket，引发连接再次超时关闭。
* **规范**：`_publish_json` 必须守卫连接状态：
  ```python
  if client is None or not self._connected:
      return
  ```

### 4. 动态虚拟点命令漏收与重启失联
* **现象**：系统下发虚拟点命令时，Adapter 报“未知命令主题”或者毫无反应；重启 Adapter 后已有虚拟点无法接收任务。
* **规范**：
  1. 订阅时**必须注册单层通配符主题**：`smartlab/adapter/{adapterName}/+/command`，兜底所有动态派生的虚拟点；
  2. Adapter 必须在同级目录维护 `virtual_leases.json`，在启动时自动读盘还原租约，停机时自动写盘保存状态快照。

### 5. 仿真推流过猛导致后端写库堵死与心跳超时
* **现象**：开启时间压缩仿真后，Adapter 很快与后端断连，系统提示心跳丢失（`AdapterHeartbeat timeout`）。
* **原因**：仿真端一次性发送成百上千个微型单点报文，后端 MQTT 回调线程被外网数据库慢查询卡住，无法在 30 秒内完成 ACK 和心跳回复。
* **规范**：必须遵守第 9.2 节的微批次规范，仿真模式打包成 `formatType: "BATCH"`，单包数据点数控制在 10~100 个，推流周期控制在 500ms~1000ms。

---

## 12. 编写检查表

- [ ] 目录结构标准：`core.py` / `simulation.py` / `control.py` / `adapterSetup.ini` / `runtime.ini`。
- [ ] ClientId 采用动态唯一化命名，绝不使用静态固定字符串。
- [ ] `_on_connect` 对错误码进行了严格校验，未连接成功绝不置 `_connected = True`。
- [ ] 订阅了通配符指令主题 `smartlab/adapter/{adapterName}/+/command`。
- [ ] 具备本地租约与物理状态自愈机制（`virtual_leases.json`）。
- [ ] 遥测报文双轨制：生产模式上报 `formatType: "SINGLE"`，仿真模式打包上报 `formatType: "BATCH"`。
- [ ] 仿真时间戳按步长递增推进（虚拟时钟保真），不直接取当前物理时间。
- [ ] 仿真打包频率与容量符合规范（推流间隔 200ms~1000ms，单包 10~100 个点）。
- [ ] `_publish_json` 增加了 `self._connected` 守卫，网络断开时不盲目塞队列。
- [ ] 仅初次启动上线时发一次 `REGISTER_TOPIC`，网络重连时不重复广播大配置。
- [ ] `control` 模块绝不接收虚拟点命令，绝不处理 LEASE，误收时立即快速失败。
- [ ] 每条 RELEASE 都严格回送 `leaseresult`（RELEASED 或 FAILED+errorMessage），绝不静默丢失。
- [ ] Setup 契约仅声明物理点，绝不包含 `virtual=true` 或预留虚拟点。

