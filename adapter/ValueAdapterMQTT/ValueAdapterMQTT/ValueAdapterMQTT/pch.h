// pch.h
#pragma once

// 修复 Windows.h 导致的 min/max 宏冲突
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN

// 包含 Winsock
#include <winsock2.h>
#include <ws2tcpip.h> 
#include <windows.h> 
// 【新增】包含用于处理路径的 API (例如 PathRemoveFileSpec)
#include <Shlwapi.h> 

// 链接 Winsock 库
#pragma comment(lib, "Ws2_32.lib")
// 【新增】链接 Shlwapi 库
#pragma comment(lib, "Shlwapi.lib")