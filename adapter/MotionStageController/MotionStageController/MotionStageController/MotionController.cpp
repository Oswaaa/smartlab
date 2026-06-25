#include "pch.h"
#include "MotionController.h"
#include <winhttp.h>

#pragma comment(lib, "winhttp.lib")

MultiCard g_MultiCard;
UINT StateUpdateLoop(LPVOID v);

MotionController::MotionController()
{
    isRunning = false;
    iMC_card = -1;
    reset = 0;
    ready = 0;
    autoreseset = 0;
    m_stateThread = nullptr;
    m_homingTaskId = "";
    m_mqttClient = nullptr;

    for (int i = 1; i <= 3; ++i) {
        m_jogRunning[i] = false;
    }
}

MotionController::~MotionController()
{
    Shutdown();
    if (m_mqttClient) delete m_mqttClient;
}

// -------------------------------------------------------------------------
// 精确时间戳日志记录
// -------------------------------------------------------------------------
void MotionController::Log(const std::string& message)
{
    std::lock_guard<std::mutex> lock(m_logMutex);
    auto now = std::chrono::system_clock::now();
    std::time_t now_c = std::chrono::system_clock::to_time_t(now);
    struct tm parts;
    localtime_s(&parts, &now_c);

    std::cout << std::put_time(&parts, "%Y-%m-%d %H:%M:%S") << " | " << message << std::endl;
}

bool MotionController::Init()
{
    Log("Application starting...");

    char m_directory[MAX_PATH];
    GetCurrentDirectoryA(MAX_PATH, (LPSTR)m_directory);
    Appdir = m_directory;

    Log("Reading configuration from setup.ini");
    char iniPath[MAX_PATH];
    sprintf_s(iniPath, "%s\\setup.ini", Appdir.c_str());


    //  读取 MQTT 配置项，支持备用默认值
    char brokerBuf[128], devIdBuf[64], userBuf[64], pwdBuf[64];
    GetPrivateProfileStringA("MQTT", "BrokerURI", "tcp://127.0.0.1:1883", brokerBuf, sizeof(brokerBuf), iniPath);
    GetPrivateProfileStringA("MQTT", "DeviceID", "reactor_001", devIdBuf, sizeof(devIdBuf), iniPath);
    GetPrivateProfileStringA("MQTT", "Username", "admin", userBuf, sizeof(userBuf), iniPath);
    GetPrivateProfileStringA("MQTT", "Password", "123456", pwdBuf, sizeof(pwdBuf), iniPath);

    m_brokerUri = brokerBuf;
    m_deviceId = devIdBuf;
    m_mqttUsername = userBuf;
    m_mqttPassword = pwdBuf;

    Log("Initializing Motion Card...");
    if (!InitMotionCard()) {
        Log("ERROR: Open Card Fail. Please check PC IP address or connection!");
        return false;
    }

    if (!InitMqtt()) {
        Log("ERROR: Connect to MQTT Broker Fail.");
        return false;
    }

    isRunning = true;
    m_stateThread = AfxBeginThread(StateUpdateLoop, this);
    Log("System initialized and running.");
    return true;
}

bool MotionController::InitMotionCard()
{
    // 采用与旧版完全一致的稳定读取方式
    char str4[50];
    char localIPstr[255], cardIPstr[255];
    int localPort, cardPort;

    std::string iniPathStr = Appdir + "\\setup.ini";

    GetPrivateProfileStringA("CARD", "LOCALIP", "192.168.0.200", localIPstr, 255, iniPathStr.c_str());
    GetPrivateProfileStringA("CARD", "CARDIP", "192.168.0.199", cardIPstr, 255, iniPathStr.c_str()); // 恢复 199 默认值

    GetPrivateProfileStringA("CARD", "LOCALPORT", "60000", str4, 50, iniPathStr.c_str());
    localPort = atoi(str4);

    GetPrivateProfileStringA("CARD", "CARDPORT", "60000", str4, 50, iniPathStr.c_str());
    cardPort = atoi(str4);

    Log(std::string("Attempting to connect card -> Local: ") + localIPstr + ":" + std::to_string(localPort) +
        " | Target: " + cardIPstr + ":" + std::to_string(cardPort));

    g_MultiCard.MC_StartDebugLog(0);
    int iRes = g_MultiCard.MC_Open(1, localIPstr, localPort, cardIPstr, cardPort);
    if (iRes) return false;

    iMC_card = 1;
    g_MultiCard.MC_SetStopDec(1, 0.5, 5);
    g_MultiCard.MC_SetStopDec(2, 0.5, 5);
    g_MultiCard.MC_SetStopDec(3, 0.5, 5);
    g_MultiCard.MC_LmtsOn(1, -1);
    g_MultiCard.MC_LmtsOn(2, -1);
    g_MultiCard.MC_LmtsOn(3, -1);
    g_MultiCard.MC_LmtSns(0);
    g_MultiCard.MC_AxisOn(1);
    g_MultiCard.MC_AxisOn(2);
    g_MultiCard.MC_AxisOn(3);

    Log("Open Card Successful!");
    return true;
}

bool MotionController::InitMqtt()
{
    m_mqttClient = new mqtt::async_client(m_brokerUri, m_deviceId);
    m_mqttClient->set_callback(*this);

    mqtt::connect_options connOpts;
    connOpts.set_keep_alive_interval(20);
    connOpts.set_clean_session(true);

    // 核心注入：携带账号密码去连接 MQTT
    if (!m_mqttUsername.empty()) {
        connOpts.set_user_name(m_mqttUsername);
        connOpts.set_password(m_mqttPassword);
    }

    try {
        m_mqttClient->connect(connOpts)->wait();
        std::string cmdTopic = "smartlab/device/+/cmd"; // 改为 '+' 通配符，接收总线上所有的指令
        m_mqttClient->subscribe(cmdTopic, 1)->wait();
        Log("MQTT Subscribed to Wildcard Topic: " + cmdTopic);
        return true;
    }
    catch (const mqtt::exception& exc) {
        Log("MQTT Exception: " + std::string(exc.what()));
        return false;
    }
}

void MotionController::connection_lost(const std::string& cause) {
    Log("MQTT Connection lost: " + cause);
}

void MotionController::Run()
{
    std::cin.get();
    Shutdown();
}

void MotionController::Shutdown()
{
    isRunning = false;
    StopAllAxes();
    if (iMC_card == 1) {
        g_MultiCard.MC_Close();
        iMC_card = -1;
    }
    if (m_mqttClient && m_mqttClient->is_connected()) {
        m_mqttClient->disconnect()->wait();
    }
}

// -------------------------------------------------------------------------
// MQTT 指令解析路由
// -------------------------------------------------------------------------
// -------------------------------------------------------------------------
// MQTT 指令解析路由 (支持一主多从架构)
// -------------------------------------------------------------------------
void MotionController::message_arrived(mqtt::const_message_ptr msg)
{
    std::string payload = msg->get_payload_str();
    Log("Received Command: " + payload);

    try {
        json j = json::parse(payload);
        std::string taskId = j.value("taskId", "SYS_MANUAL");
        std::string operation = j.value("operation", "");

        //  核心提取：提取报文中的 deviceId，如果未找到，则退化使用本地网关名
        std::string targetDevId = j.value("deviceId", m_deviceId);

        // 告知后端：对应孪生体的指令已收到
        PublishEvent(taskId, targetDevId, "evt_ack", "Command parsed: " + operation);

        if (operation == "MoveAbsolute") {
            int axis = j["parameters"].value("axis", 1);
            int position = j["parameters"].value("position", 0);
            //  透传 targetDevId 给异步线程
            std::thread(&MotionController::MoveAbsoluteAsync, this, taskId, targetDevId, axis, position).detach();
        }
        else if (operation == "ResetMotion") {
            m_homingTaskId = taskId;
            PublishEvent(taskId, targetDevId, "evt_started", "Hardware homing sequence started.");
            ResetMotion();
        }
        else if (operation == "Stop") {
            PublishEvent(taskId, targetDevId, "evt_started", "Stopping all axes...");
            StopAllAxes();
            PublishEvent(taskId, targetDevId, "evt_success", "Axes stopped successfully.");
        }
        else if (operation == "SetExtDoBit") {
            int bitIndex = j["parameters"].value("BitIndex", 0);
            int value = j["parameters"].value("Value", 0);

            // 核心修复：开启独立线程去执行 IO 与延时，绝对不卡死 MQTT 网络总线！
            std::thread([this, taskId, targetDevId, bitIndex, value]() {

                PublishEvent(taskId, targetDevId, "evt_started", "Setting IO bit...");

                // 执行硬件动作
                g_MultiCard.MC_SetExtDoBit(0, bitIndex, value);

                // 完美保留 9号气缸（气动矫正器）的 2 秒保压时间。
                // 现在它在自己的线程里睡，不会影响任何其他通信！
                if (bitIndex == 9) {
                    Sleep(2000);
                }

                PublishEvent(taskId, targetDevId, "evt_success", "IO Bit set successfully.");

                }).detach();
        }
        else if (operation == "VisionCheck") {
            int currentTopNo = j["parameters"].value("currentTopNo", 0);
            // 透传 targetDevId 给异步视觉检查线程
            std::thread(&MotionController::VisionCheckAsync, this, taskId, targetDevId, currentTopNo).detach();
        }
        else if (operation == "Jog") {
            int axis = j["parameters"].value("axis", 1);
            int dir = j["parameters"].value("dir", 1);
            double speed = j["parameters"].value("speed", 10.0);

            if (axis >= 1 && axis <= 3) {
                m_jogLastHeartbeat[axis] = std::chrono::steady_clock::now();
                if (!m_jogRunning[axis]) {
                    m_jogRunning[axis] = true;
                    TJogPrm jogPrm; jogPrm.dAcc = 0.5; jogPrm.dDec = 0.5; jogPrm.dSmooth = 0.1;
                    g_MultiCard.MC_PrfJog(axis);
                    g_MultiCard.MC_SetJogPrm(axis, &jogPrm);
                    g_MultiCard.MC_SetVel(axis, dir == 1 ? speed : -speed);
                    g_MultiCard.MC_Update(1 << (axis - 1));
                }
                PublishEvent(taskId, targetDevId, "evt_success", "Jog heartbeat accepted.");
            }
        }
        else if (operation == "JogStop") {
            int axis = j["parameters"].value("axis", 1);
            if (axis >= 1 && axis <= 3) {
                m_jogRunning[axis] = false;
                g_MultiCard.MC_Stop(1 << (axis - 1), 1 << (axis - 1));
                PublishEvent(taskId, targetDevId, "evt_success", "Jog stopped.");
            }
        }
        else {
            PublishEvent(taskId, targetDevId, "evt_error", "Unknown operation code.");
        }
    }
    catch (std::exception& e) {
        Log(std::string("JSON Parse Error: ") + e.what());
        // 如果连 JSON 都解析失败，只能以默认网关身份上报错误
        PublishEvent("unknown", m_deviceId, "evt_error", "JSON Error");
    }
}

// -------------------------------------------------------------------------
// 异步动作类
// -------------------------------------------------------------------------
void MotionController::MoveAbsoluteAsync(std::string taskId, std::string targetDeviceId, int axis, int targetPos)
{
    if (iMC_card != 1) {
        PublishEvent(taskId, targetDeviceId, "evt_error", "Motion card not initialized.");
        return;
    }

    PublishEvent(taskId, targetDeviceId, "evt_started", "Moving axis " + std::to_string(axis));
    TTrapPrm TrapPrm;
    TrapPrm.acc = (axis == 1 || axis == 3) ? 0.05 : 0.07;
    TrapPrm.dec = TrapPrm.acc;
    TrapPrm.smoothTime = 50;
    TrapPrm.velStart = 0;

    g_MultiCard.MC_PrfTrap(axis);
    g_MultiCard.MC_SetTrapPrm(axis, &TrapPrm);
    g_MultiCard.MC_SetPos(axis, targetPos);
    g_MultiCard.MC_SetVel(axis, axis == 1 ? 80 : (axis == 2 ? 25 : 3));
    g_MultiCard.MC_Update(0X0001 << (axis - 1));

    int retryCount = 0;
    int maxRetry = 100; // 10秒超时
    TAllSysStatusDataSX statusData;

    while (isRunning && retryCount < maxRetry) {
        g_MultiCard.MC_GetAllSysStatusSX(&statusData);
        if (abs(statusData.lAxisPrfPos[axis - 1] - targetPos) < 10) {
            PublishEvent(taskId, targetDeviceId, "evt_success", "Target position reached.");
            return;
        }
        Sleep(100);
        retryCount++;
    }
    if (retryCount >= maxRetry) PublishEvent(taskId, targetDeviceId, "evt_error", "Motion timeout.");
}

void MotionController::VisionCheckAsync(std::string taskId, std::string targetDeviceId, int currentTopNo)
{
    PublishEvent(taskId, targetDeviceId, "evt_started", "Vision checking...");
    CStringA rawStatus;
    bool isOk = VisionCheckInsertOk(rawStatus, currentTopNo, 10, 200);
    if (isOk) {
        PublishEvent(taskId, targetDeviceId, "evt_success", "Vision check pass.");
    }
    else {
        PublishEvent(taskId, targetDeviceId, "evt_error", std::string("Vision check fail: ") + rawStatus.GetString());
    }
}

void MotionController::PublishEvent(const std::string& taskId, const std::string& targetDeviceId, const std::string& value, const std::string& message)
{
    std::lock_guard<std::mutex> lock(m_mqttMutex);
    json evt;
    evt["taskId"] = taskId;
    evt["deviceId"] = targetDeviceId; //  修改点：使用传进来的目标设备 ID
    evt["interface"] = "adapter_evt_in";
    evt["value"] = value;
    evt["message"] = message;

    //  修改点：拼装并发送到对应的实体专属 topic
    std::string topic = "smartlab/device/" + targetDeviceId + "/evt";
    m_mqttClient->publish(mqtt::make_message(topic, evt.dump(), 1, false));
}

void MotionController::ResetMotion()
{
    g_MultiCard.MC_SetSoftLimit(1, -2147483645, 0x7fffffff);
    g_MultiCard.MC_SetSoftLimit(2, -2147483645, 0x7fffffff);
    g_MultiCard.MC_SetSoftLimit(3, -2147483645, 0x7fffffff);
    ready = 0;
    reset = 1;
    autoreseset = 0;
}

void MotionController::StopAllAxes()
{
    g_MultiCard.MC_Stop(0XFFFF, 0XFFFF);
    for (int i = 1; i <= 3; ++i) m_jogRunning[i] = false;
}

// -------------------------------------------------------------------------
// 全局守护线程：遥测 + 看门狗 + 复位状态机
// -------------------------------------------------------------------------
UINT StateUpdateLoop(LPVOID v)
{
    MotionController* pCtrl = (MotionController*)v;
    int telemetryTick = 0;

    while (pCtrl && pCtrl->isRunning)
    {
        auto now = std::chrono::steady_clock::now();
        for (int i = 1; i <= 3; ++i) {
            if (pCtrl->m_jogRunning[i]) {
                auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - pCtrl->m_jogLastHeartbeat[i]).count();
                if (elapsed > 500) {
                    pCtrl->Log("WARNING: Jog Watchdog timeout for axis " + std::to_string(i));
                    g_MultiCard.MC_Stop(1 << (i - 1), 1 << (i - 1));
                    pCtrl->m_jogRunning[i] = false;
                }
            }
        }

        TAllSysStatusDataSX m_AllSysStatusDataTemp;
        g_MultiCard.MC_GetAllSysStatusSX(&m_AllSysStatusDataTemp);
        long state;
        g_MultiCard.MC_GetDiRaw(MC_LIMIT_NEGATIVE, &state);

        if (telemetryTick++ % 10 == 0) {
            long limitPstate;
            g_MultiCard.MC_GetDiRaw(MC_LIMIT_POSITIVE, &limitPstate);

            std::lock_guard<std::mutex> lock(pCtrl->m_mqttMutex);
            json tele;
            tele["deviceId"] = pCtrl->m_deviceId;
            tele["timestamp"] = std::time(nullptr);
            json dataObj;
            for (int i = 0; i < 3; i++) {
                std::string prefix = "axis" + std::to_string(i + 1) + "_";
                dataObj[prefix + "pos"] = m_AllSysStatusDataTemp.lAxisPrfPos[i];
                dataObj[prefix + "status"] = m_AllSysStatusDataTemp.lAxisStatus[i];
            }
            dataObj["limitNstate"] = state;
            dataObj["limitPstate"] = limitPstate;
            tele["data"] = dataObj;

            std::string topic = "smartlab/device/" + pCtrl->m_deviceId + "/telemetry";
            pCtrl->m_mqttClient->publish(mqtt::make_message(topic, tele.dump(), 0, false));
        }

        if (m_AllSysStatusDataTemp.lAxisStatus[0] & AXIS_STATUS_RUNNING) { Sleep(100); continue; }
        if (m_AllSysStatusDataTemp.lAxisStatus[1] & AXIS_STATUS_RUNNING) { Sleep(100); continue; }
        if (m_AllSysStatusDataTemp.lAxisStatus[2] & AXIS_STATUS_RUNNING) { Sleep(100); continue; }

        if (pCtrl->reset) {
            if (pCtrl->autoreseset == 0) {
                TJogPrm m_JogPrm; m_JogPrm.dAcc = 0.1; m_JogPrm.dDec = 0.1; m_JogPrm.dSmooth = 0;
                g_MultiCard.MC_PrfJog(1); g_MultiCard.MC_SetJogPrm(1, &m_JogPrm); g_MultiCard.MC_SetVel(1, -pCtrl->speed[0]); g_MultiCard.MC_Update(0X0001 << 0);
                pCtrl->autoreseset++;
            }
            else if (pCtrl->autoreseset == 1) {
                if (!(state & 0x1)) { Sleep(100); continue; }
                g_MultiCard.MC_ZeroPos(1);
                TTrapPrm TrapPrm; TrapPrm.acc = 10; TrapPrm.dec = 1; TrapPrm.smoothTime = 0; TrapPrm.velStart = 0;
                g_MultiCard.MC_PrfTrap(1); g_MultiCard.MC_SetTrapPrm(1, &TrapPrm); g_MultiCard.MC_SetPos(1, 50000); g_MultiCard.MC_SetVel(1, 1500); g_MultiCard.MC_Update(0X0001 << 0);
                pCtrl->autoreseset++;
            }
            else if (pCtrl->autoreseset == 2) {
                if (state & 0x1) { Sleep(100); continue; }
                TJogPrm m_JogPrm; m_JogPrm.dAcc = 0.1; m_JogPrm.dDec = 0.1; m_JogPrm.dSmooth = 0;
                g_MultiCard.MC_PrfJog(1); g_MultiCard.MC_SetJogPrm(1, &m_JogPrm); g_MultiCard.MC_SetVel(1, -25); g_MultiCard.MC_Update(0X0001 << 0);
                pCtrl->autoreseset++;
            }
            else if (pCtrl->autoreseset == 3) {
                if (!(state & 0x1)) { Sleep(100); continue; }
                g_MultiCard.MC_Stop(0XFFFF, 0XFFFF);
                g_MultiCard.MC_ZeroPos(1);
                pCtrl->autoreseset++;
            }
            else if (pCtrl->autoreseset == 4) {
                TJogPrm m_JogPrm; m_JogPrm.dAcc = 0.1; m_JogPrm.dDec = 0.1; m_JogPrm.dSmooth = 0;
                g_MultiCard.MC_PrfJog(2); g_MultiCard.MC_SetJogPrm(2, &m_JogPrm); g_MultiCard.MC_SetVel(2, -25); g_MultiCard.MC_Update(0X0001 << 1);
                g_MultiCard.MC_PrfJog(3); g_MultiCard.MC_SetJogPrm(3, &m_JogPrm); g_MultiCard.MC_SetVel(3, -15); g_MultiCard.MC_Update(0X0001 << 2);
                pCtrl->autoreseset++;
            }
            else if (pCtrl->autoreseset == 5) {
                if (!(state & 0x2) || !(state & 0x4)) { Sleep(100); continue; }
                g_MultiCard.MC_ZeroPos(2); g_MultiCard.MC_ZeroPos(3);
                TTrapPrm TrapPrm; TrapPrm.acc = 0.1; TrapPrm.dec = 0.1; TrapPrm.velStart = 0;
                TrapPrm.smoothTime = 150;
                g_MultiCard.MC_PrfTrap(2); g_MultiCard.MC_SetTrapPrm(2, &TrapPrm); g_MultiCard.MC_SetPos(2, 5000); g_MultiCard.MC_SetVel(2, 50); g_MultiCard.MC_Update(0X0001 << 1);
                TrapPrm.smoothTime = 0;
                g_MultiCard.MC_PrfTrap(3); g_MultiCard.MC_SetTrapPrm(3, &TrapPrm); g_MultiCard.MC_SetPos(3, 1000); g_MultiCard.MC_SetVel(3, 50); g_MultiCard.MC_Update(0X0001 << 2);
                pCtrl->autoreseset++;
            }
            else if (pCtrl->autoreseset == 6) {
                if ((state & 0x2) || (state & 0x4)) { Sleep(100); continue; }
                TJogPrm m_JogPrm; m_JogPrm.dAcc = 0.1; m_JogPrm.dDec = 0.1; m_JogPrm.dSmooth = 0;
                g_MultiCard.MC_PrfJog(2); g_MultiCard.MC_SetJogPrm(2, &m_JogPrm); g_MultiCard.MC_SetVel(2, -1); g_MultiCard.MC_Update(0X0001 << 1);
                g_MultiCard.MC_PrfJog(3); g_MultiCard.MC_SetJogPrm(3, &m_JogPrm); g_MultiCard.MC_SetVel(3, -1); g_MultiCard.MC_Update(0X0001 << 2);
                pCtrl->autoreseset++;
            }
            else if (pCtrl->autoreseset == 7) {
                if (!(state & 0x2) || !(state & 0x4)) { Sleep(100); continue; }
                g_MultiCard.MC_ZeroPos(1); g_MultiCard.MC_ZeroPos(2); g_MultiCard.MC_ZeroPos(3);
                pCtrl->reset = 0;
                pCtrl->ready = 1;
                g_MultiCard.MC_SetSoftLimit(1, -1000, 1900000);
                g_MultiCard.MC_SetSoftLimit(2, -100, 430000);
                g_MultiCard.MC_SetSoftLimit(3, -10, 45000);

                if (!pCtrl->m_homingTaskId.empty()) {
                    pCtrl->PublishEvent(pCtrl->m_homingTaskId, pCtrl->m_deviceId, "evt_success", "Hardware homing completed.");
                    pCtrl->m_homingTaskId = "";
                }
            }
        }
        Sleep(100);
    }
    return 0;
}

// -------------------------------------------------------------------------
// 视觉检测实现
// -------------------------------------------------------------------------
bool MotionController::HttpGetLocalhost8089Status(CStringA& outBody)
{
    bool bRet = false;
    HINTERNET hSession = WinHttpOpen(L"MotionController/1.0", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (hSession) {
        HINTERNET hConnect = WinHttpConnect(hSession, L"127.0.0.1", 8089, 0);
        if (hConnect) {
            HINTERNET hRequest = WinHttpOpenRequest(hConnect, L"GET", L"/status", NULL, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, 0);
            if (hRequest) {
                if (WinHttpSendRequest(hRequest, WINHTTP_NO_ADDITIONAL_HEADERS, 0, WINHTTP_NO_REQUEST_DATA, 0, 0, 0)) {
                    if (WinHttpReceiveResponse(hRequest, NULL)) {
                        DWORD dwSize = 0;
                        DWORD dwDownloaded = 0;
                        outBody.Empty();
                        do {
                            dwSize = 0;
                            if (!WinHttpQueryDataAvailable(hRequest, &dwSize)) break;
                            if (dwSize == 0) break;
                            char* pszOutBuffer = new char[dwSize + 1];
                            if (WinHttpReadData(hRequest, (LPVOID)pszOutBuffer, dwSize, &dwDownloaded)) {
                                pszOutBuffer[dwDownloaded] = '\0';
                                outBody += pszOutBuffer;
                            }
                            delete[] pszOutBuffer;
                        } while (dwSize > 0);
                        bRet = true;
                    }
                }
                WinHttpCloseHandle(hRequest);
            }
            WinHttpCloseHandle(hConnect);
        }
        WinHttpCloseHandle(hSession);
    }
    return bRet;
}

bool MotionController::VisionCheckInsertOk(CStringA& outRawStatus, int currentTopNo, int maxRetry, int intervalMs)
{
    auto splitByChar = [](const CStringA& s, char c) {
        std::vector<CStringA> res;
        int start = 0;
        while (start < s.GetLength()) {
            int pos = s.Find(c, start);
            if (pos < 0) { res.push_back(s.Mid(start)); break; }
            res.push_back(s.Mid(start, pos - start));
            start = pos + 1;
        }
        return res;
        };

    for (int i = 0; i < maxRetry; ++i) {
        CStringA body;
        if (HttpGetLocalhost8089Status(body)) {
            body.Trim();
            outRawStatus = body;
            auto parts = splitByChar(body, '_');
            if (parts.size() >= 2) {
                CStringA tagOk = parts[0]; tagOk.MakeLower();
                CStringA allOk = parts[1]; allOk.MakeLower();
                if (tagOk == "true" && allOk == "true") return true;
            }
        }
        Sleep(intervalMs);
    }
    return false;
}