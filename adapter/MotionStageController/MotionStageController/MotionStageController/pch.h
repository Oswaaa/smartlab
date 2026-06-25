#ifndef PCH_H
#define PCH_H

#define NOMINMAX // 防止污染 std::max

// MFC 核心组件
#include <afxwin.h>
#include <iostream>
#include <string>
#include <vector>
#include <map>
#include <atlstr.h>
#include <mutex>
#include <atomic>
#include <chrono>
#include <iomanip>

// JSON 与 MQTT 现代库
#include <nlohmann/json.hpp>
#include <mqtt/async_client.h>

// 引入运动控制卡库
#include "MultiCard.h"

// 前向声明核心类
class MotionController;

#endif //PCH_H