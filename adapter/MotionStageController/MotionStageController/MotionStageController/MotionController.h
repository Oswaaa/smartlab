#pragma once
#include "pch.h"

using json = nlohmann::json;

class MotionController : public virtual mqtt::callback
{
public:
    MotionController();
    ~MotionController();

    // --- 公共接口 ---
    bool Init();
    void Run();
    void Shutdown();
    void Log(const std::string& message);

    // --- 公共状态变量 ---
    std::string Appdir;
    std::atomic<bool> isRunning;
    int iMC_card;

    std::atomic<int> reset;
    std::atomic<int> ready;
    std::atomic<int> autoreseset;
    double speed[3] = { 50.0, 25.0, 15.0 };

    std::string m_homingTaskId;

    // --- Jog 安全控制核心变量 (看门狗) ---
    std::map<int, std::chrono::steady_clock::time_point> m_jogLastHeartbeat;
    std::map<int, bool> m_jogRunning;

    // --- 工业物联网配置 ---
    mqtt::async_client* m_mqttClient;
    std::string m_deviceId;
    std::string m_brokerUri;
    std::string m_mqttUsername; // ? 新增：MQTT 用户名
    std::string m_mqttPassword; // ? 新增：MQTT 密码
    std::mutex m_mqttMutex;
    std::mutex m_logMutex;

    // --- 视觉检测接口 ---
    bool HttpGetLocalhost8089Status(CStringA& outBody);
    bool VisionCheckInsertOk(CStringA& outRawStatus, int currentTopNo, int maxRetry = 10, int intervalMs = 200);

    void message_arrived(mqtt::const_message_ptr msg) override;
    void connection_lost(const std::string& cause) override;
    void PublishEvent(const std::string& taskId, const std::string& targetDeviceId, const std::string& value, const std::string& message);

private:
    // --- 硬件私有方法 ---
    bool InitMotionCard();
    void ResetMotion();
    void StopAllAxes();

    // --- MQTT 网络层与异步指令方法 ---
    bool InitMqtt();
    void MoveAbsoluteAsync(std::string taskId, std::string targetDeviceId, int axis, int targetPos);
    void VisionCheckAsync(std::string taskId, std::string targetDeviceId, int currentTopNo);

    // --- 配置缓存 ---
    char m_localIPstr[32];
    char m_cardIPstr[32];
    int m_localPort;
    int m_cardPort;

    CWinThread* m_stateThread;
};