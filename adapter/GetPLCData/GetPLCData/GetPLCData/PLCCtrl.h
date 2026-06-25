#pragma once
#include "pch.h"
#include <iostream>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <thread>
#include <Windows.h>
#include <vector>
#include <string>
#include <tchar.h>
#include <mutex>
#include <atomic>
#include <sstream>
#include <algorithm>

// PostgreSQL
#include <libpq-fe.h>
#pragma comment(lib, "libpq.lib")
#pragma comment(lib, "ws2_32.lib")

#include <curl/curl.h>
#include <nlohmann/json.hpp>
#include <set>
// --- 命名空间简化 ---
using json = nlohmann::json;

// --- 用于存储从后端获取的写入规则 ---
struct WriteConfig {
    std::string targetTable;
    std::string sqlTemplate;
};

// --- 核心修正: 确保DataPointConfig包含channel成员 ---
struct DataPointConfig {
    std::string internal_key;
    std::string deviceName;
    std::string dataType;
    int channel;
};

// --- 用于缓存在后端解析好的写入规则 ---
using WriteConfigCache = std::map<std::string, WriteConfig>;


// 读取 INI
// --- 修改: AdapterConfig 结构体, 移除旧的db.targets, 新增api_host ---
struct AdapterConfig {
    struct {
        std::string name;
        std::string host;
        int         port;
        std::string desc;
        std::string cmdTpl;
        std::string api_host; // 新增API主机地址
    } adapter;

    struct {
        std::string ip485 = "192.168.0.201";
        int         port485 = 26;
        std::string ipPLC = "192.168.0.10";
        int         portPLC = 502;
    } socket;

    struct {
        std::string host;
        int port = 5432;
        std::string dbname;
        std::string user;
        std::string password;
    } db;
};

// [新增] 定义执行模式枚举
enum class ExecutionMode {
    INSTANT, // 即时返回 (不等待PLC响应/不启动监控)
    TARGET,  // 目标监控 (数值达到目标值即完成)
    PROFILE  // 过程监控 (数值达标 且 维持指定时长)
};

// 添加前向声明
class PLCCtrl;

// 用于传递给监控线程的数据结构
// [修改] 结构体重命名 HeatMonitorData -> TempMonitorData
struct TempMonitorData {
    PLCCtrl* controller; // 现在编译器知道 PLCCtrl 是个类了
    SOCKET clientSocket;
    std::string requestId;
    int deviceType; // 0=反应釜, 1=保温箱, 2=色谱, 3=热阱, 4=气体流量

    // 联合参数
    int channel;    // 通道号
    int deviceId;   // 设备ID (保温箱用)
    int targetStep; // 分段号 / 流量段
    double targetValue; // 目标温度 或 流量值
    int targetTime; // 持续时间 (分钟, 仅反应釜用)

    // [新增] 模式和超时
    ExecutionMode mode; // 执行模式
    int timeoutSec;     // 自定义超时时间(秒)
};

// 全局配置（读写需加锁）
extern AdapterConfig g_cfg;

class PLCCtrl
{
public:
    PLCCtrl();
    ~PLCCtrl();

    void socketSeverListenPort();
    void socketClientTo485Hnadler();
    void socketClientToPLCHnadler();
    int my_recv_data(SOCKET clientSocket, char* buffer, int len);

    int sendPresscommand(unsigned char ch, unsigned char dir, double speed, uint32_t pluse);
    int stop(unsigned char ch);
    int get_state(unsigned char ch);
    int clear_alarm(unsigned char ch);
    int set_para(unsigned char ch);

    static uint16_t byteConversion(uint16_t val);
    static uint32_t byteConversion(uint32_t val);
    uint16_t calc_nb_modbus_crc(uint8_t buf[], uint16_t lenth);
    //0成功 1失败
    int getPLCData();
    int set_tmp_target(unsigned char ch, double target);
    int AdjustValueOut(unsigned char ch);
    int write_single_reg(uint16_t addr, uint16_t val);
    int set_reactor_step(int channel, int step, int temperature, int minutes);
    int handle_command_line(const std::string& line, SOCKET sock);
    int set_manual_auto(int enableWrite);
    int set_channel_start(unsigned char ch, int start);
    // [新增] 智能启动加热：确保通道处于启动状态
    int ensure_heating_started(unsigned char ch);
    // [新增 3] 监控线程函数声明 (静态)
    static void MonitorProcessThread(TempMonitorData* pData);

    // 辅助函数：读取单个保持寄存器
    int read_single_register(uint16_t addr, int16_t& val_out);
    // [新增] 批量读取寄存器函数 (Modbus 0x03)
    int read_multi_registers(uint16_t startAddr, uint16_t count, uint16_t* outBuf);
    // [新增] 解析缓存数据到结构体
    void parse_plc_data();

    // 设置两个保温箱的控温
    int set_incubator_temp(int incubator_id, int temperature);
    // 设置八个通道的反应报警温度
    int set_reaction_alarm_temp(int channel, int temperature);
    // 设置热阱报警温度（前四个）
    int set_heatsink_alarm_temp(int heatsink_id, int temperature);
    // 设置两个保温箱报警温度
    int set_incubator_alarm_temp(int incubator_id, int temperature);
    // 设置九个PLC使能
    int set_plc_enable(int enable_id, int status);
    // 设置气体流量（八通道，每通道两段）
    int set_gas_flow(int channel, int segment, int flow_rate);
    // 获取实时气体流量
    int get_realtime_gas_flow(int channel, int segment, float& flow_value);
    // 读取色谱伴热温度
    int get_chromatography_temp(float& temp_value);
    // 设置色谱报警温度
    int set_chromatography_alarm_temp(int temperature);
    // 设置色谱温度
    int set_chromatography_temp(int temperature);
    // 读取热阱温度
    int get_heatsink_temp(int heatsink_id, float& temp_value);
    // 获取保温箱温度
    int get_incubator_temp(int sensor_id, float& temp_value);
    // 设置热阱控温
    int set_heatsink_temp(int channel, int temperature);
    // 设置报警消音
    int set_alarm_mute(int status);
    // 设置一氧化碳阀状态
    int set_co_valve(int status);
    // 设置氢气阀状态
    int set_h2_valve(int status);

    //// [新增] 声明监控线程函数为友元或静态成员，这里使用静态成员
    //static UINT MonitorProcessThread(LPVOID param);
    // [新增 4] 启动异步监控的辅助函数 (封装重复代码)
    // 更新声明以包含 mode 和 timeout
    void StartAsyncMonitor(SOCKET sock, std::string reqId, int devType,
        double targetVal, int ch = 0, int devId = 0, int step = 0, int time = 0,
        ExecutionMode mode = ExecutionMode::TARGET, int timeout = 0);

public:
    SOCKET serverSocket = NULL;
    SOCKET clientSocket485 = NULL;
    SOCKET clientSocketPLC = NULL;

    unsigned char PLC_ID = 1;
    double  kp = 50;
    double  ki = 0;//暂时设置为0
    double  kd = 0.01;

    struct press_ctrl_t {
        double targetPress = 0; //目标值
        double cllctPress = 0;  //采集值
        double lastErr[3] = { 0 }; //上次误差值
        int cllct_no_err = 0;   //读取成功标志
        int targetChanged = 0;  //重新设置了目标压力值
        int isTargetReached = 0;// 目标达成标志 (0 = 正在调节中, 1 = 已到达目标)
        int moter_no_responce;  //电机没有回复的标志
        int moter_alarm;        //电机报警
        double I = 0;//用于pid积分
    };
    struct press_ctrl_t press_ctrl[8];

    struct tmp_ctrl_t {
        int targetTmp[10] = { 0 };//每段目标温度
        int targetTime[10] = { 0 };//每段加热时间
        double cllctTmp = 0;//实时温度采集值
        int maxConfiguredStep = 0;//记录当前通道配置到的最大步骤号
    };
    struct tmp_ctrl_t tmp_ctrl[8];

    // --- 存储所有需要采集的数据 ---
    double gas_flow[8][2];     // 8个通道，每通道2段气体流量
    double chromatography_temp; // 色谱伴热温度
    double heatsink_temps[8];   // 8个热阱温度
    double incubator_temps[2];  // 2个保温箱温度

private:
    // PLC 寄存器镜像缓存 (覆盖 0 - 296 地址)
    // 使用 300 大小以确保安全
    uint16_t m_plc_mirror[300];

    // 配置与监听控制
    std::atomic<bool> cfgThreadRun{ false };
    std::thread       cfgThread;
    std::mutex        cfgMutex;
    std::string       iniPath;
    //PostgreSQL
    PGconn* pgConn = nullptr;
    //锁
    std::mutex        plcMutex;   // 用于保护PLC Socket
    std::mutex        dataMutex;  // 用于保护 press_ctrl 和 tmp_ctrl

    // --- 动态配置相关成员变量 ---
    std::vector<DataPointConfig> m_active_data_points;      // 从INI解析出的激活数据点
    std::map<std::string, double*> m_value_pointers;        // 内部代号 -> 数据变量地址的映射
    WriteConfigCache m_write_config_cache;
    std::set<std::string> m_prepared_statements;

    // 配置加载/工具
    AdapterConfig load_config_from_ini(const std::string& path);
    std::string detect_local_ipv4();
    std::string exe_dir();
    void config_refresher_loop();

    //PostgreSQL
    bool db_ping();
    bool init_db();
    void close_db();

    // 用于新的数据插入流程 ---
    void initialize_value_pointer_map();
    void resolve_all_mappings_at_startup();
    bool resolve_mapping_from_backend(const DataPointConfig& dp);
    void insert_reading(const DataPointConfig& dp, double value);
    void insert_all_readings_batch();
    static size_t WriteCallback(void* contents, size_t size, size_t nmemb, void* userp);

    // 用于控制保存频率，避免文件爆炸
    time_t last_save_time = 0;
    // 声明保存为CSV的函数
    void saveDataToCSV();
};