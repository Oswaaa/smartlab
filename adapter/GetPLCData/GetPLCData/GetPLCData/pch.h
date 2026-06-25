// pch.h —— 纯 Win32 版（不依赖 MFC）
#pragma once
#ifndef PCH_H
#define PCH_H

// ---- 你的自定义消息（保留）----
#ifndef WM_TIICON
#define WM_TIICON  (WM_USER + 1)
#endif
#ifndef WM_LIST1MSG
#define WM_LIST1MSG (WM_USER + 2)
#endif
#ifndef WM_EDIT1MSG
#define WM_EDIT1MSG (WM_USER + 3)
#endif

// ---- 系统与网络头 ----
#define WIN32_LEAN_AND_MEAN
#include "winsock2.h"
#include "ws2tcpip.h"
#include "windows.h"

#include "cstdio"
#include "cstdint"
#include "iostream"
#include "thread"
#include "vector"
#include "string"

// ---- 原 pch.h 的结构体（去掉 MFC 的 CWnd*，改为 HWND）----
typedef struct {
    HWND   dlg1;          // was CWnd*，改为 HWND，若项目中从未使用该字段也可以保留为 nullptr
    int    iItem;
    int    devicetype;
    char   upperIP[50];
    int    upperPort;
    SOCKET hsocket;
    void* socket1;
    void* socket2;
} STU1;

typedef struct {
    int    iAxis;
    int    TargetPos;
    void* socket2;
} STU2;

// ---- WinSock 库 ----
#pragma comment(lib, "ws2_32.lib")

#endif // PCH_H
