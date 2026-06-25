// PLCCtrl.h

#pragma once
#include "pch.h"

#include <mqtt/async_client.h>
#include <json/json.h> 
#include <thread>
#include <atomic>
#include <map>
#include <mutex>
#include <condition_variable> // �����������ڵȴ� PLC ȷ��
#include <set>
#include <array> 

// AppConfig �ṹ��
struct AppConfig
{
    std::string tenant_id;
    std::string lab_id;
    std::string device_type;
    std::string model_id;
    std::string instance_id;
    std::string device_sn;
    std::string mqtt_broker;
    std::string mqtt_client_id;
    std::string mqtt_user;
    std::string mqtt_pass;
    // --- ���޸ġ�---
    std::set<std::string> plc_sub_topics; // �� std::string ��Ϊ std::set<std::string>
    // --- ���޸Ľ�����---
    std::string adapter_pub_topic;
    std::string smartlab_cmd_topic;
    std::string smartlab_evt_topic;
    std::string smartlab_status_topic;
    std::string smartlab_telemetry_topic;
    std::string smartlab_alarm_topic;
    std::string smartlab_heartbeat_topic;
    int         tcp_server_port;
};

class MQTTAdapter
{
public:
    MQTTAdapter(const AppConfig& config);
    ~MQTTAdapter();

private:
    const AppConfig m_config;
    // MQTT
    mqtt::async_client               mqtt_client;
    mqtt::connect_options            conn_opts;
    std::atomic<bool>                mqtt_connected{ false };

    // TCP ������
    SOCKET                           server_socket = INVALID_SOCKET;
    std::atomic<bool>                server_running{ true };

    // �����������ڴ洢 PLC �Ĵ���״̬
    std::map<std::string, int>       m_plc_register_state;
    std::mutex                       m_state_mutex; // ���� m_plc_register_state

    // �����������ڵȴ� PLC ״̬ȷ��
    std::condition_variable          m_state_cv;
    std::mutex                       m_cv_mutex; // ר������ m_state_cv �Ļ�����


    // �߳�
    std::thread                      mqtt_thread;
    std::thread                      tcp_server_thread;

    // ��������
    static std::string make_json(const std::string& requestId,
        const std::string& status,
        const std::string& dataKV = "",
        const std::string& err = "");

    void mqtt_worker();
    void tcp_server_worker();

    // ���޸ġ��������� (ǩ������)
    bool publish_tagdata_message(int mw_index, int value);
    bool publish_json_message(const std::string& topic, const Json::Value& root, int qos = 1, bool retained = false);
    // ���޸ġ������ (ǩ������)
    void handle_tcp_command(const std::string& line, SOCKET client);

    // --- ������������� order=1 �����߼� ---
    void valve_control_operation(int ch, int value, std::string& errorMsg);
    void handle_smartlab_command(const std::string& topic, const std::string& payload);
    void publish_smartlab_event(const std::string& eventId,
        const std::string& commandId,
        const std::string& correlationId,
        const std::string& commandState,
        const std::string& operationState,
        const Json::Value& payload,
        const std::string& errorCode = "",
        const std::string& errorMessage = "");
    void publish_smartlab_status(const std::string& commandState, const std::string& operationState, bool retained = true);
    void publish_smartlab_telemetry();
    void publish_smartlab_heartbeat();
    Json::Value smartlab_base_message(const std::string& messageType) const;
    
    // --- ���������ָ�������صĻص����� ---
    void on_mqtt_message(mqtt::const_message_ptr msg);
    void process_plc_json(const std::string& payload);
    // --- ������������ ---

public:
    void run();
};
