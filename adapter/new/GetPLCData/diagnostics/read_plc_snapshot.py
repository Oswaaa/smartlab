"""Read-only diagnostic: Modbus function 03, Addr_0..Addr_296. No writes."""
import configparser
import datetime
import json
from pathlib import Path
import socket
import struct
import time


def receive(sock, count):
    data = b''
    while len(data) < count:
        block = sock.recv(count - len(data))
        if not block:
            raise ConnectionError('PLC closed connection')
        data += block
    return data


def main():
    root = Path(__file__).resolve().parent.parent
    cfg = configparser.ConfigParser()
    cfg.read(root / 'runtime.ini', encoding='utf-8')
    host, port = cfg['plc']['ip'], int(cfg['plc']['port'])
    result = {'timestamp': datetime.datetime.now().astimezone().isoformat(),
              'host': host, 'port': port, 'functionCode': 3, 'registers': {}, 'errors': []}
    try:
        with socket.create_connection((host, port), timeout=3) as sock:
            sock.settimeout(3)
            for tid, start in enumerate(range(0, 297, 30), 1):
                count = min(30, 297 - start)
                sock.sendall(struct.pack('>HHHBBHH', tid, 0, 6, 1, 3, start, count))
                header = receive(sock, 7)
                rtid, proto, length, unit = struct.unpack('>HHHB', header)
                if not 2 <= length <= 254:
                    raise RuntimeError('Invalid MBAP length')
                pdu = receive(sock, length - 1)
                if rtid != tid or proto != 0 or unit != 1:
                    raise RuntimeError('Invalid MBAP response')
                if pdu[0] & 0x80:
                    raise RuntimeError('PLC exception ' + str(pdu[1]))
                if pdu[:2] != bytes([3, count * 2]) or len(pdu) != 2 + count * 2:
                    raise RuntimeError('Invalid register response')
                stamp = datetime.datetime.now().astimezone().isoformat()
                for i in range(count):
                    raw = struct.unpack_from('>H', pdu, 2 + i * 2)[0]
                    result['registers'][str(start + i)] = {
                        'unsigned': raw, 'signed': raw if raw < 32768 else raw - 65536,
                        'readAt': stamp}
                time.sleep(0.05)
    except Exception as error:
        result['errors'].append(type(error).__name__ + ': ' + str(error))
    path = root / 'diagnostics' / 'plc-live-read.json'
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'count': len(result['registers']), 'errors': result['errors'],
                      'timestamp': result['timestamp'], 'path': str(path)}, ensure_ascii=True))


if __name__ == '__main__':
    main()
