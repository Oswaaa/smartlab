#include "pch.h"
#include "PLCCtrl.h"

int main() {

    // 构造里会启动三个线程：8080 服务器、485 客户端、PLC 客户端
    PLCCtrl app;

    std::cout << "Running... PLC Ctrl+C to exit.\n";
    for (;;) {
        Sleep(1000);
    }
    return 0;
}
