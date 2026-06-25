// ValueAdapterMQTT.cpp

#include "pch.h"
#include "PLCCtrl.h"
#include <iostream> // 确保包含

// 获取 .ini 文件的完整路径 (假设 setup.ini 与 .exe 在同一目录)
std::string get_ini_path()
{
    char exePath[MAX_PATH];
    GetModuleFileNameA(NULL, exePath, MAX_PATH); // 使用 'A' (ANSI) 版本
    PathRemoveFileSpecA(exePath); // 使用 'A' (ANSI) 版本

    char iniPath[MAX_PATH];
    PathCombineA(iniPath, exePath, "setup.ini"); // 使用 'A' (ANSI) 版本
    return std::string(iniPath);
}

// --- 【新增】辅助函数：按逗号拆分主题字符串 ---
static std::set<std::string> split_topics(const std::string& s)
{
    std::set<std::string> result;
    std::stringstream ss(s);
    std::string item;
    while (std::getline(ss, item, ',')) {
        // 移除可能的空格
        size_t a = item.find_first_not_of(" \t\r\n");
        if (a == std::string::npos) continue;
        size_t b = item.find_last_not_of(" \t\r\n");
        std::string trimmed = item.substr(a, b - a + 1);

        if (!trimmed.empty()) {
            result.insert(trimmed);
        }
    }
    return result;
}
// --- 【新增结束】 ---

static std::string make_smartlab_topic(const AppConfig& config, const std::string& channel)
{
    return "smartlab/v1/" + config.tenant_id + "/" + config.lab_id + "/" +
        config.device_type + "/" + config.model_id + "/" + config.device_sn + "/" + channel;
}

int main()
{
    // 创建控制台
    AllocConsole();
    freopen_s((FILE**)stdout, "CONOUT$", "w", stdout);
    freopen_s((FILE**)stdin, "CONIN$", "r", stdin);

    std::string iniPath = get_ini_path();
    std::cout << "Loading configuration from: " << iniPath << std::endl;

    // 【新增】读取配置
    AppConfig config;
    char buffer[1024];

    // [Server]
    config.tcp_server_port = GetPrivateProfileIntA(
        "Server", "Port", 8080, iniPath.c_str()
    );

    // [MQTT]
    GetPrivateProfileStringA("MQTT", "Broker", "tcp://192.168.0.90:1883", buffer, sizeof(buffer), iniPath.c_str());
    config.mqtt_broker = buffer;

    GetPrivateProfileStringA("MQTT", "ClientID", "adapter1_client", buffer, sizeof(buffer), iniPath.c_str());
    config.mqtt_client_id = buffer;

    GetPrivateProfileStringA("MQTT", "User", "adapter1", buffer, sizeof(buffer), iniPath.c_str());
    config.mqtt_user = buffer;

    GetPrivateProfileStringA("MQTT", "Password", "adapter1", buffer, sizeof(buffer), iniPath.c_str());
    config.mqtt_pass = buffer;

    // --- 【修改】按照 setup.ini，从 [MQTT] 读取 DeviceSN ---
    GetPrivateProfileStringA("MQTT", "TenantID", "default", buffer, sizeof(buffer), iniPath.c_str());
    config.tenant_id = buffer;

    GetPrivateProfileStringA("MQTT", "LabID", "lab1", buffer, sizeof(buffer), iniPath.c_str());
    config.lab_id = buffer;

    GetPrivateProfileStringA("MQTT", "DeviceType", "PLC", buffer, sizeof(buffer), iniPath.c_str());
    config.device_type = buffer;

    GetPrivateProfileStringA("MQTT", "ModelID", "PLC_VALVE_ARRAY", buffer, sizeof(buffer), iniPath.c_str());
    config.model_id = buffer;

    GetPrivateProfileStringA("MQTT", "InstanceID", "", buffer, sizeof(buffer), iniPath.c_str());
    config.instance_id = buffer;

    GetPrivateProfileStringA("MQTT", "DeviceSN", "default_sn", buffer, sizeof(buffer), iniPath.c_str());
    config.device_sn = buffer;
    if (config.instance_id.empty()) {
        config.instance_id = config.device_sn;
    }

    // [Topics]
   // --- 【修改】读取逗号分隔的字符串并拆分 ---
    GetPrivateProfileStringA("Topics", "PlcSubscribe", "lab1/plc/plc0001", buffer, sizeof(buffer), iniPath.c_str());
    config.plc_sub_topics = split_topics(buffer); // 调用拆分函数
    // --- 【修改结束】 ---

    GetPrivateProfileStringA("Topics", "AdapterPublish", "lab1/adapter/plc0001", buffer, sizeof(buffer), iniPath.c_str());
    config.adapter_pub_topic = buffer;

    std::string cmdDefault = make_smartlab_topic(config, "cmd/+");
    GetPrivateProfileStringA("Topics", "SmartLabCommand", cmdDefault.c_str(), buffer, sizeof(buffer), iniPath.c_str());
    config.smartlab_cmd_topic = buffer;

    std::string evtDefault = make_smartlab_topic(config, "evt");
    GetPrivateProfileStringA("Topics", "SmartLabEvent", evtDefault.c_str(), buffer, sizeof(buffer), iniPath.c_str());
    config.smartlab_evt_topic = buffer;

    std::string statusDefault = make_smartlab_topic(config, "status");
    GetPrivateProfileStringA("Topics", "SmartLabStatus", statusDefault.c_str(), buffer, sizeof(buffer), iniPath.c_str());
    config.smartlab_status_topic = buffer;

    std::string telemetryDefault = make_smartlab_topic(config, "telemetry");
    GetPrivateProfileStringA("Topics", "SmartLabTelemetry", telemetryDefault.c_str(), buffer, sizeof(buffer), iniPath.c_str());
    config.smartlab_telemetry_topic = buffer;

    std::string alarmDefault = make_smartlab_topic(config, "alarm");
    GetPrivateProfileStringA("Topics", "SmartLabAlarm", alarmDefault.c_str(), buffer, sizeof(buffer), iniPath.c_str());
    config.smartlab_alarm_topic = buffer;

    std::string heartbeatDefault = make_smartlab_topic(config, "heartbeat");
    GetPrivateProfileStringA("Topics", "SmartLabHeartbeat", heartbeatDefault.c_str(), buffer, sizeof(buffer), iniPath.c_str());
    config.smartlab_heartbeat_topic = buffer;


    // 打印加载的配置
    std::cout << "--- Loaded Configuration ---" << std::endl;
    std::cout << "[Server] Port: " << config.tcp_server_port << std::endl;
    std::cout << "[MQTT] Broker: " << config.mqtt_broker << std::endl;
    std::cout << "[MQTT] User: " << config.mqtt_user << std::endl;
    std::cout << "[MQTT] TenantID: " << config.tenant_id << std::endl;
    std::cout << "[MQTT] LabID: " << config.lab_id << std::endl;
    std::cout << "[MQTT] DeviceType: " << config.device_type << std::endl;
    std::cout << "[MQTT] ModelID: " << config.model_id << std::endl;
    std::cout << "[MQTT] DeviceSN: " << config.device_sn << std::endl; // 【新增】
    // --- 【修改】循环打印所有订阅的主题 ---
    std::cout << "[Topics] Subscribing to:" << std::endl;
    if (config.plc_sub_topics.empty()) {
        std::cout << "  (None)" << std::endl;
    }
    else {
        for (const auto& topic : config.plc_sub_topics) {
            std::cout << "  - " << topic << std::endl;
        }
    }
    // --- 【修改结束】 ---
    std::cout << "[Topics] Pub: " << config.adapter_pub_topic << std::endl;
    std::cout << "[Topics] SmartLabCommand: " << config.smartlab_cmd_topic << std::endl;
    std::cout << "[Topics] SmartLabEvent: " << config.smartlab_evt_topic << std::endl;
    std::cout << "[Topics] SmartLabStatus: " << config.smartlab_status_topic << std::endl;
    std::cout << "[Topics] SmartLabTelemetry: " << config.smartlab_telemetry_topic << std::endl;
    std::cout << "-----------------------------" << std::endl;


    // 【修改】将配置传入
    MQTTAdapter app(config);
    app.run();

    return 0;
}
