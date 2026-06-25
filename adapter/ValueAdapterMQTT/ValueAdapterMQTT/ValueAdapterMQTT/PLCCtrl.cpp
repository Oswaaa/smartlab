#include "pch.h"
#include "PLCCtrl.h"

// --- ⬇️ 添加这个代码块以修复 LNK2001 ⬇️ ---
#include <mqtt/message.h>
namespace mqtt {
    const std::string message::EMPTY_STR;
}
// --- ⬆️ 添加完毕 ⬆️ ---

#include <iostream>
#include <sstream>
#include <iomanip>
#include <chrono>

// ----------------------------------------------------
// 工具函数 (从 V2 借鉴)
// ----------------------------------------------------
static std::string trim_copy(const std::string& s)
{
    size_t a = s.find_first_not_of(" \r\n\t");
    if (a == std::string::npos) return {};
    size_t b = s.find_last_not_of(" \r\n\t");
    return s.substr(a, b - a + 1);
}

static std::string get_param(const std::string& qs, const std::string& key)
{
    size_t pos = 0;
    while (pos < qs.size()) {
        size_t amp = qs.find('&', pos);
        std::string kv = qs.substr(pos, amp == std::string::npos ? std::string::npos : amp - pos);
        size_t eq = kv.find('=');
        if (eq != std::string::npos) {
            std::string k = kv.substr(0, eq);
            if (k == key) return kv.substr(eq + 1);
        }
        if (amp == std::string::npos) break;
        pos = amp + 1;
    }
    return {};
}

static std::string now_iso8601()
{
    SYSTEMTIME st;
    GetLocalTime(&st);
    char buf[64];
    sprintf_s(buf, "%04d-%02d-%02dT%02d:%02d:%02d.%03d+08:00",
        st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);
    return std::string(buf);
}

static std::string json_string(const Json::Value& root, bool pretty = false)
{
    Json::StreamWriterBuilder builder;
    builder["indentation"] = pretty ? "  " : "";
    return Json::writeString(builder, root);
}

std::string MQTTAdapter::make_json(const std::string& requestId,
    const std::string& status,
    const std::string& dataKV,
    const std::string& err)
{
    std::string data = dataKV.empty() ? "{}" : ("{" + dataKV + "}");
    return "{\"requestId\":\"" + requestId +
        "\",\"status\":\"" + status +
        "\",\"data\":" + data +
        ",\"error\":\"" + err + "\"}";
}

// ----------------------------------------------------
// 构造 / 析构
// ----------------------------------------------------
MQTTAdapter::MQTTAdapter(const AppConfig& config)
    : m_config(config),
    mqtt_client(config.mqtt_broker, config.mqtt_client_id)
{
    conn_opts.set_user_name(config.mqtt_user);
    conn_opts.set_password(config.mqtt_pass);
    conn_opts.set_keep_alive_interval(20);
    conn_opts.set_clean_session(true);
    conn_opts.set_automatic_reconnect(true);

    mqtt_client.set_connection_lost_handler([this](const std::string&) {
        std::cout << "[MQTT] Connection lost, reconnecting..." << std::endl;
        mqtt_connected.store(false);
        });

    mqtt_client.set_message_callback([this](mqtt::const_message_ptr msg) {
        on_mqtt_message(msg);
        });
}

MQTTAdapter::~MQTTAdapter()
{
    server_running.store(false);
    if (server_socket != INVALID_SOCKET) closesocket(server_socket);
    if (mqtt_client.is_connected()) mqtt_client.disconnect()->wait();

    if (mqtt_thread.joinable()) mqtt_thread.join();
    if (tcp_server_thread.joinable()) tcp_server_thread.join();

    WSACleanup();
}

// ----------------------------------------------------
// MQTT 主线程
// ----------------------------------------------------
void MQTTAdapter::mqtt_worker()
{
    while (true) {
        try {
            if (!mqtt_connected) {
                std::cout << "[MQTT] Connecting to " << m_config.mqtt_broker << "..." << std::endl;
                mqtt_client.connect(conn_opts)->wait();

                std::cout << "[MQTT] Subscribing to topics..." << std::endl;
                for (const auto& topic : m_config.plc_sub_topics)
                {
                    if (!topic.empty()) {
                        std::cout << "[MQTT]  - Subscribing to: " << topic << std::endl;
                        mqtt_client.subscribe(topic, 1)->wait();
                    }
                }
                if (!m_config.smartlab_cmd_topic.empty()) {
                    std::cout << "[MQTT]  - Subscribing to SmartLab command: " << m_config.smartlab_cmd_topic << std::endl;
                    mqtt_client.subscribe(m_config.smartlab_cmd_topic, 1)->wait();
                }

                mqtt_connected.store(true);
                std::cout << "[MQTT] Connected and Subscribed" << std::endl;
                publish_smartlab_status("IDLE", "Idle", true);
                publish_smartlab_heartbeat();
            }
        }
        catch (const mqtt::exception& exc) {
            std::cerr << "[MQTT] Exception: " << exc.what() << std::endl;
            mqtt_connected.store(false);
        }
        std::this_thread::sleep_for(std::chrono::seconds(5));
    }
}

// ----------------------------------------------------
// TCP 服务器线程 (与 Java 系统交互)
// ----------------------------------------------------
void MQTTAdapter::tcp_server_worker()
{
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return;

    server_socket = socket(AF_INET, SOCK_STREAM, 0);
    if (server_socket == INVALID_SOCKET) return;

    int opt = 1;
    setsockopt(server_socket, SOL_SOCKET, SO_REUSEADDR, (char*)&opt, sizeof(opt));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(m_config.tcp_server_port);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (bind(server_socket, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR ||
        listen(server_socket, SOMAXCONN) == SOCKET_ERROR) {
        std::cerr << "[TCP] bind/listen failed on port " << m_config.tcp_server_port << std::endl;
        return;
    }

    std::cout << "[TCP] Listening on port " << m_config.tcp_server_port << " (for Java system)" << std::endl;

    while (server_running) {
        sockaddr_in client_addr{};
        int len = sizeof(client_addr);
        SOCKET client = accept(server_socket, (sockaddr*)&client_addr, &len);
        if (client == INVALID_SOCKET) continue;

        char ipbuf[INET_ADDRSTRLEN]{};
        inet_ntop(AF_INET, &client_addr.sin_addr, ipbuf, sizeof(ipbuf));
        std::cout << "[TCP] Client connected: " << ipbuf << std::endl;

        std::string buffer;
        char recvbuf[4096];

        while (server_running) {
            int n = recv(client, recvbuf, sizeof(recvbuf) - 1, 0);
            if (n <= 0) break;
            recvbuf[n] = 0;
            buffer.append(recvbuf, n);

            size_t pos = 0;
            while (true) {
                size_t nl = buffer.find('\n', pos);
                if (nl == std::string::npos) break;
                std::string line = buffer.substr(pos, nl - pos);
                if (!line.empty()) handle_tcp_command(line, client);
                pos = nl + 1;
            }
            if (pos) buffer.erase(0, pos);
        }
        closesocket(client);
        std::cout << "[TCP] Client disconnected: " << ipbuf << std::endl;
    }
}

// ----------------------------------------------------
// 【重大修改】处理来自 Java 系统的命令 (已重构)
// ----------------------------------------------------
void MQTTAdapter::handle_tcp_command(const std::string& line, SOCKET client)
{
    std::string cmd = trim_copy(line);
    if (cmd.empty()) return;

    std::cout << "[Java → Adapter] " << cmd << std::endl;

    std::string requestId = get_param(cmd, "requestId");
    if (requestId.empty()) requestId = "unknown";

    // 1. 立即返回 received (不变)
    std::string ack = make_json(requestId, "received", "", "") + "\n";
    send(client, ack.c_str(), (int)ack.size(), 0);

    // 2. 解析命令 (不变)
    std::string orderStr = get_param(cmd, "order");
    int order = orderStr.empty() ? -1 : std::atoi(orderStr.c_str());

    std::string dataKV = "";
    std::string errorMsg = "";

    if (order == 1) {
        // --- 【修改】order=1 逻辑 ---
        // order=1&ch={param}&Value={param} (控制 MW0-MW7)
        std::string chStr = get_param(cmd, "ch");
        std::string valStr = get_param(cmd, "Value");

        dataKV = "\"ch\":" + (chStr.empty() ? "null" : chStr) +
            ",\"Value\":" + (valStr.empty() ? "null" : valStr);

        int ch = std::atoi(chStr.c_str());
        int value = std::atoi(valStr.c_str());

        // 3. 验证参数
        if (ch < 1 || ch > 8) { errorMsg = "Invalid parameter: 'ch' must be between 1 and 8."; }
        else if (value != 0 && value != 1) { errorMsg = "Invalid parameter: 'Value' must be 0 or 1."; }
        else {
            // 4. 【修改】调用解耦的函数
            //    此函数将处理 映射、发布 和 等待确认
            //    并
            valve_control_operation(ch, value, errorMsg);
        }
        // --- 【修改结束】 ---
    }
    else if (order == 2) {
        // order=2: 读取所有电磁阀的状态 (MW20 - MW27)
        // (此逻辑保持不变)
        std::stringstream ss_data;
        bool first = true;

        std::lock_guard<std::mutex> lock(m_state_mutex);

        std::cout << "[Adapter] Reading status (order=2) from MW20-MW27..." << std::endl;
        for (int i = 20; i <= 27; ++i) {
            std::string key = "MW" + std::to_string(i);
            int status = 0;

            auto it = m_plc_register_state.find(key);
            if (it != m_plc_register_state.end()) {
                status = it->second;
            }

            if (!first) {
                ss_data << ",";
            }
            ss_data << "\"" << key << "\":" << status;
            first = false;
        }

        dataKV = ss_data.str();
        std::cout << "[Adapter] Current state: {" << dataKV << "}" << std::endl;
    }
    else {
        errorMsg = "unknown order";
    }

    // --- 等待确认的逻辑块已移至 process_order_1_control ---

    // 6. 发送最终 "finished" 或 "error" 响应
    //    (此逻辑保持不变)
    std::string resp = make_json(requestId, (errorMsg.empty() ? "finished" : "error"), dataKV, errorMsg) + "\n";
    send(client, resp.c_str(), (int)resp.size(), 0);

    // 成功日志现在由 process_order_1_control 内部处理
}

// ----------------------------------------------------
// 【新增】解耦的 Order=1 逻辑 (控制电磁阀)
// ----------------------------------------------------
void MQTTAdapter::valve_control_operation(int ch, int value, std::string& errorMsg)
{
    // 1. 映射逻辑 (ch 1-8 对应 MW0-MW7)
    int mw_index = ch - 1;
    std::string target_mw_key = "MW" + std::to_string(mw_index);
    int target_mw_value = value;

    // 2. 发布 MQTT
    bool publish_ok = publish_tagdata_message(mw_index, value);
    if (!publish_ok) {
        errorMsg = "mqtt publish failed";
        return;
    }

    // 3. 等待 PLC 确认
    std::cout << "[Adapter] Waiting for PLC confirmation for "
        << target_mw_key << "=" << target_mw_value << "..." << std::endl;

    bool confirmed = false;
    auto start_time = std::chrono::steady_clock::now();
    // 设置 5 秒超时
    auto timeout = std::chrono::seconds(5);

    // 持续检查，直到确认或超时
    while (std::chrono::steady_clock::now() - start_time < timeout)
    {
        // 3.1. 立即检查当前状态
        {
            std::lock_guard<std::mutex> lock(m_state_mutex);
            auto it = m_plc_register_state.find(target_mw_key);
            // 检查键是否存在，并且值是否等于我们设置的目标值
            if (it != m_plc_register_state.end() && it->second == target_mw_value) {
                confirmed = true;
                break; // 已确认，跳出循环
            }
        } // 释放 m_state_mutex 锁

        // 3.2. 如果未确认, 等待新消息的通知
        // 我们使用一个专用的 m_cv_mutex 来配合 m_state_cv
        std::unique_lock<std::mutex> cv_lock(m_cv_mutex);
        // 等待 250 毫秒，或者被 process_plc_json 唤醒
        if (m_state_cv.wait_for(cv_lock, std::chrono::milliseconds(250)) == std::cv_status::no_timeout)
        {
            // 被唤醒了, 循环将立即重新开始，并重新检查第 1 步中的状态
        }
    } // 结束 while 循环

    if (confirmed) {
        std::cout << "[Adapter] Confirmation received: "
            << target_mw_key << " is now " << target_mw_value << std::endl;
        errorMsg = ""; // 成功, 确保 errorMsg 为空
    }
    else {
        std::cout << "[Adapter] PLC confirmation timeout." << std::endl;
        errorMsg = "PLC confirmation timeout for " + target_mw_key;
    }
}



// ----------------------------------------------------
// 【重大修改】下发控制指令到 PLC
// ----------------------------------------------------
bool MQTTAdapter::publish_tagdata_message(int mw_index, int value)
{
    if (!mqtt_connected.load()) {
        std::cerr << "[MQTT] Cannot publish, not connected." << std::endl;
        return false;
    }

    // 1. 构建 JSON 格式: [{"DeviceSN":"...","TagData":[{"MWxx":value}]}]

    // 1.1 创建内部 TagData: {"MW0": 1}
    Json::Value tag;
    tag["MW" + std::to_string(mw_index)] = value;

    // 1.2 创建 TagData 数组: [ {"MW0": 1} ]
    Json::Value tagData_arr(Json::arrayValue);
    tagData_arr.append(tag);

    // 1.3 创建根对象: {"DeviceSN": "...", "TagData": [ ... ]}
    Json::Value root_obj;
    root_obj["DeviceSN"] = m_config.device_sn; //
    root_obj["TagData"] = tagData_arr;

    // 1.4 创建根数组: [ { ... } ]
    Json::Value root_arr(Json::arrayValue);
    root_arr.append(root_obj);

    // 2. 序列化
    Json::StreamWriterBuilder builder;
    builder["indentation"] = ""; // 紧凑格式
    std::string payload = Json::writeString(builder, root_arr);

    // 3. 发布
    try {
        std::cout << "[MQTT] Publishing to " << m_config.adapter_pub_topic << ": " << payload << std::endl;
        mqtt::message_ptr pubmsg = mqtt::make_message(m_config.adapter_pub_topic, payload); //
        pubmsg->set_qos(1);
        mqtt_client.publish(pubmsg)->wait(); // 使用 wait() 确保发送
        return true;
    }
    catch (const mqtt::exception& exc) {
        std::cerr << "[MQTT] Error publishing message: " << exc.what() << std::endl;
        return false;
    }
}

bool MQTTAdapter::publish_json_message(const std::string& topic, const Json::Value& root, int qos, bool retained)
{
    if (!mqtt_connected.load()) {
        std::cerr << "[MQTT] Cannot publish SmartLab message, not connected." << std::endl;
        return false;
    }
    try {
        std::string payload = json_string(root);
        mqtt::message_ptr pubmsg = mqtt::make_message(topic, payload);
        pubmsg->set_qos(qos);
        pubmsg->set_retained(retained);
        mqtt_client.publish(pubmsg)->wait();
        std::cout << "[SmartLab MQTT] Publishing to " << topic << ": " << payload << std::endl;
        return true;
    }
    catch (const mqtt::exception& exc) {
        std::cerr << "[SmartLab MQTT] Error publishing message: " << exc.what() << std::endl;
        return false;
    }
}

Json::Value MQTTAdapter::smartlab_base_message(const std::string& messageType) const
{
    Json::Value root;
    root["specVersion"] = "smartlab.adapter.v1";
    root["messageType"] = messageType;
    root["timestamp"] = now_iso8601();
    root["tenantId"] = m_config.tenant_id;
    root["labId"] = m_config.lab_id;

    Json::Value device;
    device["deviceType"] = m_config.device_type;
    device["modelId"] = m_config.model_id;
    device["deviceSn"] = m_config.device_sn;
    device["instanceId"] = m_config.instance_id;
    root["device"] = device;
    return root;
}

void MQTTAdapter::publish_smartlab_event(const std::string& eventId,
    const std::string& commandId,
    const std::string& correlationId,
    const std::string& commandState,
    const std::string& operationState,
    const Json::Value& payload,
    const std::string& errorCode,
    const std::string& errorMessage)
{
    Json::Value root = smartlab_base_message("EVENT");
    root["eventId"] = eventId;
    root["correlationId"] = correlationId;
    Json::Value command;
    command["commandId"] = commandId;
    root["command"] = command;
    Json::Value status;
    status["commandState"] = commandState;
    status["operationState"] = operationState;
    status["online"] = true;
    root["status"] = status;
    root["payload"] = payload;
    if (!errorCode.empty() || !errorMessage.empty()) {
        Json::Value err;
        err["code"] = errorCode;
        err["message"] = errorMessage;
        root["error"] = err;
    }
    publish_json_message(m_config.smartlab_evt_topic, root, 1, false);
}

void MQTTAdapter::publish_smartlab_status(const std::string& commandState, const std::string& operationState, bool retained)
{
    Json::Value root = smartlab_base_message("STATUS");
    root["eventId"] = "status_update";
    Json::Value status;
    status["commandState"] = commandState;
    status["operationState"] = operationState;
    status["online"] = true;
    status["health"] = "OK";
    root["status"] = status;
    publish_json_message(m_config.smartlab_status_topic, root, 1, retained);
}

void MQTTAdapter::publish_smartlab_telemetry()
{
    Json::Value root = smartlab_base_message("TELEMETRY");
    Json::Value payload;
    {
        std::lock_guard<std::mutex> lock(m_state_mutex);
        for (const auto& item : m_plc_register_state) {
            payload[item.first] = item.second;
        }
    }
    root["payload"] = payload;
    publish_json_message(m_config.smartlab_telemetry_topic, root, 0, false);
}

void MQTTAdapter::publish_smartlab_heartbeat()
{
    Json::Value root = smartlab_base_message("HEARTBEAT");
    Json::Value status;
    status["online"] = true;
    status["health"] = "OK";
    root["status"] = status;
    publish_json_message(m_config.smartlab_heartbeat_topic, root, 0, true);
}

void MQTTAdapter::handle_smartlab_command(const std::string& topic, const std::string& payload)
{
    Json::Value root;
    Json::CharReaderBuilder reader_builder;
    std::unique_ptr<Json::CharReader> reader(reader_builder.newCharReader());
    std::string errs;

    if (!reader->parse(payload.c_str(), payload.c_str() + payload.size(), &root, &errs)) {
        Json::Value errPayload;
        errPayload["raw"] = payload;
        publish_smartlab_event("failed", "unknown", "", "FAILED", "Fault", errPayload, "INVALID_JSON", errs);
        return;
    }

    std::string commandId;
    if (root.isMember("command") && root["command"].isObject()) {
        commandId = root["command"].get("commandId", "").asString();
    }
    if (commandId.empty()) {
        commandId = root.get("commandId", "").asString();
    }
    if (commandId.empty()) {
        size_t slash = topic.find_last_of('/');
        commandId = slash == std::string::npos ? "unknown" : topic.substr(slash + 1);
    }
    std::string correlationId = root.get("correlationId", "").asString();
    Json::Value commandPayload = root.isMember("payload") && root["payload"].isObject()
        ? root["payload"]
        : Json::Value(Json::objectValue);

    publish_smartlab_event("ack", commandId, correlationId, "ACCEPTED", "Running", Json::Value(Json::objectValue));
    publish_smartlab_status("RUNNING", "Running", true);

    std::string errorMsg;
    Json::Value resultPayload;
    if (commandId == "set_valve" || commandId == "start" || commandId == "open_valve" || commandId == "close_valve") {
        int ch = commandPayload.get("ch", commandPayload.get("channel", 1)).asInt();
        int value = commandPayload.get("value", commandId == "close_valve" ? 0 : 1).asInt();
        resultPayload["ch"] = ch;
        resultPayload["value"] = value;
        valve_control_operation(ch, value, errorMsg);
    }
    else if (commandId == "emergency_stop") {
        for (int ch = 1; ch <= 8; ++ch) {
            std::string oneError;
            valve_control_operation(ch, 0, oneError);
            if (!oneError.empty() && errorMsg.empty()) {
                errorMsg = oneError;
            }
        }
        resultPayload["emergencyStop"] = true;
    }
    else if (commandId == "read_status") {
        publish_smartlab_telemetry();
        resultPayload["readStatus"] = true;
    }
    else {
        errorMsg = "Unsupported commandId: " + commandId;
    }

    if (errorMsg.empty()) {
        publish_smartlab_event("completed", commandId, correlationId, "COMPLETED", "Idle", resultPayload);
        publish_smartlab_status("IDLE", "Idle", true);
    }
    else {
        publish_smartlab_event("failed", commandId, correlationId, "FAILED", "Fault", resultPayload, "COMMAND_FAILED", errorMsg);
        publish_smartlab_status("FAILED", "Fault", true);
    }
}

// ----------------------------------------------------
// 【新增】接收 PLC 上报的数据
// ----------------------------------------------------
void MQTTAdapter::on_mqtt_message(mqtt::const_message_ptr msg)
{
    const std::string topic = msg->get_topic();
    if (topic.rfind("smartlab/v1/", 0) == 0 && topic.find("/cmd/") != std::string::npos)
    {
        handle_smartlab_command(topic, msg->get_payload_str());
    }
    else if (m_config.plc_sub_topics.count(topic))
    {
        // std::cout << "[MQTT Recv " << msg->get_topic() << "] " << msg->get_payload_str() << std::endl;
        process_plc_json(msg->get_payload_str());
    }
    else
    {
        // std::cout << "[MQTT Recv] Ignoring message from unhandled topic: " << msg->get_topic() << std::endl;
    }
}

// ----------------------------------------------------
// 【新增】解析 PLC 的 JSON 数据并更新内部状态
// ----------------------------------------------------
void MQTTAdapter::process_plc_json(const std::string& payload)
{
    Json::Value root;
    Json::CharReaderBuilder reader_builder;
    std::unique_ptr<Json::CharReader> reader(reader_builder.newCharReader());
    std::string errs;

    if (!reader->parse(payload.c_str(), payload.c_str() + payload.size(), &root, &errs))
    {
        std::cerr << "[JSON Parse Error] " << errs << std::endl;
        return;
    }

    if (!root.isArray()) {
        std::cerr << "[JSON Logic Error] Root is not an array." << std::endl;
        return;
    }

    bool state_changed = false;

    try
    {
        for (const auto& root_obj : root)
        {
            if (!root_obj.isObject() || !root_obj.isMember("TagData") || !root_obj["TagData"].isArray())
            {
                continue;
            }

            const auto& tag_data_arr = root_obj["TagData"];

            for (const auto& tag_data_obj : tag_data_arr)
            {
                if (!tag_data_obj.isObject()) continue;

                std::lock_guard<std::mutex> lock(m_state_mutex);

                for (auto it = tag_data_obj.begin(); it != tag_data_obj.end(); ++it)
                {
                    std::string key = it.key().asString();

                    if (key.rfind("MW", 0) == 0)
                    {
                        if (it->isInt()) {
                            int new_value = it->asInt();
                            auto reg_it = m_plc_register_state.find(key);

                            if (reg_it == m_plc_register_state.end() || reg_it->second != new_value)
                            {
                                // std::cout << "[State Update] " << key << " = " << new_value << std::endl;
                                m_plc_register_state[key] = new_value;
                                state_changed = true;
                            }
                        }
                    }
                }
            }
        }
    }
    catch (const std::exception& e)
    {
        std::cerr << "[JSON Process Error] " << e.what() << std::endl;
    }


    if (state_changed)
    {
        std::cout << "[Adapter] PLC state updated. Notifying waiting threads..." << std::endl;
        m_state_cv.notify_all();
        publish_smartlab_telemetry();
    }
}

// ----------------------------------------------------
// 启动入口
// ----------------------------------------------------
void MQTTAdapter::run()
{
    mqtt_thread = std::thread(&MQTTAdapter::mqtt_worker, this);
    tcp_server_thread = std::thread(&MQTTAdapter::tcp_server_worker, this);

    std::cout << "MQTT PLC Adapter running (Ctrl+C to exit)\n";
    for (;;) {
        Sleep(1000);
    }
}
