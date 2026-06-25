#include "pch.h"
#include "PLCCtrl.h"

#include <fstream>    // 解决 std::ofstream 不完整类型报错
#include <ctime>      // 解决 time, localtime 报错
#include <iomanip>    // 解决格式化报错

#include <vector>
#include <sstream>
#include <algorithm>
#include <iomanip>
//#include "IniFile.h" // 确保您有这个文件或替换为您自己的INI解析实现

// 全局配置对象
AdapterConfig g_cfg;

/**************************************************************************************************/
/* 1. 辅助工具与配置函数 (Helper Utilities & Configuration Functions)                             */
/**************************************************************************************************/

/**
 * @brief libcurl库的回调函数，用于处理HTTP响应数据
 * @param contents 指向接收到的数据块的指针
 * @param size 每个数据项的大小
 * @param nmemb 数据项的数量
 * @param userp 用户提供的指针，这里指向一个std::string对象
 * @return 返回已处理的字节数
 */
size_t PLCCtrl::WriteCallback(void* contents, size_t size, size_t nmemb, void* userp)
{
	((std::string*)userp)->append((char*)contents, size * nmemb);
	return size * nmemb;
}

/**
 * @brief 从INI文件中读取字符串值
 * @param path INI文件的完整路径
 * @param section 节名，例如 "DB"
 * @param key 键名，例如 "HOST"
 * @param defv 找不到键时返回的默认字符串（默认为空）
 * @return 读取到的字符串值或默认值
 */
static std::string read_ini_stringA(const char* path, const char* section, const char* key, const char* defv = "") {
	char buf[2048] = { 0 };
	GetPrivateProfileStringA(section, key, defv, buf, sizeof(buf) - 1, path);
	return std::string(buf);
}

/**
 * @brief 从INI文件中读取整数值
 * @param path INI文件的完整路径
 * @param section 节名
 * @param key 键名
 * @param defv 找不到键时返回的默认整数值
 * @return 读取到的整数值或默认值
 */
static int read_ini_intA(const char* path, const char* section, const char* key, int defv = 0) {
	return GetPrivateProfileIntA(section, key, defv, path);
}

/**
 * @brief 按指定分隔符分割字符串
 * @param s 源字符串
 * @param delimiter 分隔符
 * @return 包含所有子字符串的vector
 */
static std::vector<std::string> split_string(const std::string& s, char delimiter) {
	std::vector<std::string> tokens;
	std::string token;
	std::istringstream tokenStream(s);
	while (std::getline(tokenStream, token, delimiter)) {
		tokens.push_back(token);
	}
	return tokens;
}

/**
 * @brief 去除字符串两端的空白字符
 * @param s 源字符串
 * @return 去除空白后的新字符串
 */
static std::string trim_string(const std::string& s) {
	const char* whitespace = " \t\n\r\f\v";
	size_t first = s.find_first_not_of(whitespace);
	if (std::string::npos == first) {
		return "";
	}
	size_t last = s.find_last_not_of(whitespace);
	return s.substr(first, (last - first + 1));
}

/**
 * @brief 去除字符串两端的空白字符，返回一个副本
 * @param s 源字符串
 * @return 去除空白后的新字符串
 */
static std::string trim_copy(const std::string& s) {
	size_t a = s.find_first_not_of(" \r\n\t");  // 找第一个非空白位置
	size_t b = s.find_last_not_of(" \r\n\t");   // 找最后一个非空白位置
	if (a == std::string::npos) return std::string();  // 空串返回空
	return s.substr(a, b - a + 1);  // 截取有效部分
}


/**
 * @brief 从URL查询字符串（如"a=1&b=2"）中提取指定键的值
 * @param qs 查询字符串
 * @param key 要查找的键
 * @return 找到的键对应的值，未找到则返回空字符串
 */
static std::string get_param(const std::string& qs, const std::string& key) {
	// 解析 "a=1&b=2" 风格的字符串; 如果存在'#'，则忽略之后的内容
	size_t sharp = qs.find('#');
	std::string q = qs.substr(0, sharp);
	size_t pos = 0;
	while (pos < q.size()) {
		size_t amp = q.find('&', pos);
		std::string kv = q.substr(pos, amp == std::string::npos ? std::string::npos : amp - pos);
		size_t eq = kv.find('=');
		if (eq != std::string::npos) {
			std::string k = kv.substr(0, eq);
			std::string v = kv.substr(eq + 1);
			if (k == key) return v;
		}
		if (amp == std::string::npos) break;
		pos = amp + 1;
	}
	return std::string();
}

/**
 * @brief 构建用于响应客户端的JSON字符串
 * @param requestId 请求ID，会原样返回
 * @param status 响应状态，如 "received", "finished"
 * @param dataKV JSON数据部分的内容，例如 "\"ch\":1,\"target\":120.5"
 * @param err 错误信息字符串，无错误则为空
 * @return 拼接好的完整JSON字符串
 */
static std::string make_json(const std::string& requestId,
	const std::string& status,
	const std::string& dataKV,
	const std::string& err) {
	std::string d = dataKV.empty() ? "{}" : ("{" + dataKV + "}");
	return std::string("{\"requestId\":\"") + requestId +
		"\",\"status\":\"" + status +
		"\",\"data\":" + d +
		",\"error\":\"" + err + "\"}";
}

/**
 * @brief 获取当前EXE文件所在的目录路径
 * @return EXE文件目录路径字符串
 */
std::string PLCCtrl::exe_dir() {
	char exePath[MAX_PATH] = { 0 };
	GetModuleFileNameA(nullptr, exePath, MAX_PATH);
	std::string dir(exePath);
	auto pos = dir.find_last_of("\\/");
	if (pos != std::string::npos) dir = dir.substr(0, pos);
	return dir;
}

/**
 * @brief 自动探测本地的非回环IPv4地址
 * @return 找到的第一个非回环IPv4地址，若失败则返回 "127.0.0.1"
 */
std::string PLCCtrl::detect_local_ipv4() {
	char hostname[256] = { 0 };
	if (gethostname(hostname, sizeof(hostname)) != 0) return "127.0.0.1";

	addrinfo hints{}; hints.ai_family = AF_INET; hints.ai_socktype = SOCK_STREAM;
	addrinfo* res = nullptr;
	if (getaddrinfo(hostname, nullptr, &hints, &res) != 0) return "127.0.0.1";

	std::string ip = "127.0.0.1";
	for (auto p = res; p; p = p->ai_next) {
		sockaddr_in* a = (sockaddr_in*)p->ai_addr;
		uint32_t v = ntohl(a->sin_addr.s_addr);
		if ((v >> 24) == 127) continue; // 跳过 127.x.x.x
		char buf[INET_ADDRSTRLEN] = { 0 };
		inet_ntop(AF_INET, &a->sin_addr, buf, sizeof(buf));
		ip = buf; break;
	}
	freeaddrinfo(res);
	return ip;
}

/**
 * @brief 字节序转换 (16位)
 * @param val 原始值
 * @return 转换后的值
 */
uint16_t PLCCtrl::byteConversion(uint16_t val)
{
	char* p = (char*)(&val);
	uint16_t ret;
	char* retp = (char*)(&ret);
	retp[0] = p[1];
	retp[1] = p[0];
	return ret;
}

/**
 * @brief 字节序转换 (32位)
 * @param val 原始值
 * @return 转换后的值
 */
uint32_t PLCCtrl::byteConversion(uint32_t val)
{
	char* p = (char*)(&val);
	uint32_t ret;
	char* retp = (char*)(&ret);
	retp[0] = p[3];
	retp[1] = p[2];
	retp[2] = p[1];
	retp[3] = p[0];
	return ret;
}

/**
 * @brief 计算Modbus RTU的CRC16校验码
 * @param buf 数据缓冲区
 * @param lenth 数据长度
 * @return 16位CRC校验码
 */
uint16_t PLCCtrl::calc_nb_modbus_crc(uint8_t buf[], uint16_t lenth)
{
	uint16_t i, j;
	uint16_t temp_crc = 0xFFFF;

	for (i = 0; i < lenth; i++) {
		temp_crc ^= buf[i];
		for (j = 0; j < 8; j++) {
			if (temp_crc & 0x01) {
				temp_crc = (temp_crc >> 1) ^ 0xA001;
			}
			else {
				temp_crc = temp_crc >> 1;
			}
		}
	}
	return temp_crc;
}

/**
 * @brief 创建一个控制台窗口并将标准输入、输出、错误重定向到该窗口
 */
void RedirectIOToConsole()
{
	AllocConsole();
	FILE* fp;
	freopen_s(&fp, "CONOUT$", "w", stdout);
	freopen_s(&fp, "CONIN$", "r", stdin);
	freopen_s(&fp, "CONOUT$", "w", stderr);
	std::ios::sync_with_stdio();
}

// --- 核心: 初始化内部代号到变量地址的硬映射 ---
void PLCCtrl::initialize_value_pointer_map()
{
	m_value_pointers.clear();
	//std::cout << "[INFO] Initializing internal value pointer map..." << std::endl;

	// 映射8个通道的反应温度、压力、质计流量
	for (int i = 0; i < 8; ++i) {
		m_value_pointers["C" + std::to_string(i + 1) + "_TEMP"] = &tmp_ctrl[i].cllctTmp;
		m_value_pointers["C" + std::to_string(i + 1) + "_PRESS"] = &press_ctrl[i].cllctPress;
		m_value_pointers["C" + std::to_string(i + 1) + "_GAS_FLOW1"] = &gas_flow[i][0];
		m_value_pointers["C" + std::to_string(i + 1) + "_GAS_FLOW2"] = &gas_flow[i][1];
	}

	// 映射8个热阱温度
	for (int i = 0; i < 8; ++i) {
		m_value_pointers["HS" + std::to_string(i + 1) + "_TEMP"] = &heatsink_temps[i];
	}

	// 映射2个保温箱温度
	m_value_pointers["INC1_TEMP"] = &incubator_temps[0];
	m_value_pointers["INC2_TEMP"] = &incubator_temps[1];

	// 映射色谱温度
	m_value_pointers["CHROM_TEMP"] = &chromatography_temp;

	std::cout << "[INFO] " << m_value_pointers.size() << " internal data sources initialized." << std::endl;
}

// ======================================================================================
// [新增] 闭环监控相关结构体与线程函数
// ======================================================================================

/**
 * @brief 启动异步监控线程的辅助封装函数
 * @param sock 客户端Socket (所有权移交)
 * @param reqId 请求ID
 * @param devType 设备类型 (0=反应通道, 1=保温箱, 2=色谱, 3=热阱, 4=气体流量)
 * @param targetVal 目标值 (温度或流量)
 * @param ch 通道号 (可选)
 * @param devId 设备ID (可选)
 * @param step 步骤/段号 (可选)
 * @param time 持续时间 (可选)
 * @param mode 执行模式 (INSTANT, TARGET, PROFILE)
 * @param timeout 超时时间(秒)，0表示使用默认策略
 */
void PLCCtrl::StartAsyncMonitor(SOCKET sock, std::string reqId, int devType,
	double targetVal, int ch, int devId, int step, int time, ExecutionMode mode, int timeout)
{
	// 模式 1: INSTANT - 立即返回 Finished，不启动线程
	if (mode == ExecutionMode::INSTANT) {
		std::string data = "{}";
		// 简单的 data 构造，为了统一格式可以根据 devType 做个简单 switch，
		// 但通常 instant 模式只要知道命令发下去了即可。
		// 这里复用一部分生成逻辑太麻烦，直接返回空 JSON 或基本信息。
		switch (devType) {
		case 0: data = "\"ch\":" + std::to_string(ch) + ",\"temp\":" + std::to_string((int)targetVal); break;
		case 1: data = "\"id\":" + std::to_string(devId) + ",\"temp\":" + std::to_string((int)targetVal); break;
		case 4: data = "\"ch\":" + std::to_string(ch) + ",\"flow\":" + std::to_string((int)targetVal); break;
		}

		std::string resp = make_json(reqId, "finished", data, "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		return;
	}

	// 模式 2 & 3: TARGET / PROFILE - 启动后台线程监控
	TempMonitorData* pData = new TempMonitorData;
	pData->controller = this;
	pData->clientSocket = sock;
	pData->requestId = reqId;
	pData->deviceType = devType;
	pData->targetValue = targetVal;
	pData->channel = ch;
	pData->deviceId = devId;
	pData->targetStep = step;
	pData->targetTime = time;
	pData->mode = mode;       // 设置模式
	pData->timeoutSec = timeout; // 设置超时时间

	std::thread t(MonitorProcessThread, pData);
	t.detach();
}

/**
 * @brief 通用监控线程：根据 ExecutionMode 执行不同的等待逻辑
 */
void PLCCtrl::MonitorProcessThread(TempMonitorData* pData) {
	PLCCtrl* ctrl = pData->controller;
	std::string err_msg = "";
	bool is_done = false;

	// --- 1. 确定超时时间 ---
	int timeout_sec = 3600; // 默认 1 小时
	if (pData->timeoutSec > 0) {
		timeout_sec = pData->timeoutSec;
	}
	else if (pData->targetTime > 0) {
		// 如果是 Profile 模式且未指定 timeout，默认给 过程时间 + 20分钟
		timeout_sec = (pData->targetTime * 60) + 1200;
	}

	// 记录开始时间 (用于 PROFILE 模式计算持续时间)
	auto start_time = std::chrono::steady_clock::now();

	// --- 2. 循环监控 ---
	while (timeout_sec > 0) {
		double currentVal = 0.0;
		bool valid = true;

		// 获取当前值
		{
			std::lock_guard<std::mutex> lock(ctrl->dataMutex);
			switch (pData->deviceType) {
			case 0: // 反应釜
				if (pData->channel >= 1 && pData->channel <= 8)
					currentVal = ctrl->tmp_ctrl[pData->channel - 1].cllctTmp;
				else valid = false;
				break;
			case 1: // 保温箱
				if (pData->deviceId >= 1 && pData->deviceId <= 2)
					currentVal = ctrl->incubator_temps[pData->deviceId - 1];
				else valid = false;
				break;
			case 2: // 色谱
				currentVal = ctrl->chromatography_temp;
				break;
			case 3: // 热阱
				if (pData->channel >= 1 && pData->channel <= 8)
					currentVal = ctrl->heatsink_temps[pData->channel - 1];
				else valid = false;
				break;
			case 4: // 气体流量
				if (pData->channel >= 1 && pData->channel <= 8 && pData->targetStep >= 1 && pData->targetStep <= 2)
					currentVal = ctrl->gas_flow[pData->channel - 1][pData->targetStep - 1];
				else valid = false;
				break;
			default:
				valid = false;
			}
		}

		if (!valid) {
			err_msg = "Invalid device/channel";
			break;
		}

		// 根据模式判断是否完成
		if (pData->mode == ExecutionMode::TARGET) {
			// TARGET模式：数值接近/到达目标即完成
			if (std::abs(currentVal - pData->targetValue) <= 0) {
				is_done = true;
				break;
			}
		}
		else if (pData->mode == ExecutionMode::PROFILE) {
			// PROFILE模式：数值达标 且 维持了足够的时间
			auto now = std::chrono::steady_clock::now();
			auto elapsed_sec = std::chrono::duration_cast<std::chrono::seconds>(now - start_time).count();

			bool value_reached = std::abs(currentVal - pData->targetValue) <= 0;
			// pData->targetTime 单位是分钟
			bool time_reached = elapsed_sec >= (pData->targetTime * 60);

			if (value_reached && time_reached) {
				is_done = true;
				break;
			}
		}

		std::this_thread::sleep_for(std::chrono::seconds(1));
		timeout_sec--;
	}

	if (!is_done && timeout_sec <= 0) {
		err_msg = "Timeout";
	}

	// --- 3. 动态生成 JSON ---
	std::string data = "";
	switch (pData->deviceType) {
	case 0: // 反应通道: {"ch":1, "step":1, "temp":100}
		data = "\"ch\":" + std::to_string(pData->channel) +
			",\"step\":" + std::to_string(pData->targetStep) +
			",\"temp\":" + std::to_string((int)pData->targetValue);
		break;
	case 1: // 保温箱: {"id":1, "temp":50}
		data = "\"id\":" + std::to_string(pData->deviceId) +
			",\"temp\":" + std::to_string((int)pData->targetValue);
		break;
	case 2: // 色谱: {"temp":100}
		data = "\"temp\":" + std::to_string((int)pData->targetValue);
		break;
	case 3: // 热阱: {"ch":1, "temp":-20}
		data = "\"ch\":" + std::to_string(pData->channel) +
			",\"temp\":" + std::to_string((int)pData->targetValue);
		break;
	case 4: // 气体流量: {"ch":1, "seg":1, "flow":50}
		data = "\"ch\":" + std::to_string(pData->channel) +
			",\"seg\":" + std::to_string(pData->targetStep) +
			",\"flow\":" + std::to_string((int)pData->targetValue);
		break;
	default: data = "{}"; break;
	}

	std::string status = is_done ? "finished" : "failed";
	std::string resp = make_json(pData->requestId, status, data, err_msg);
	resp.push_back('\n');
	send(pData->clientSocket, resp.c_str(), (int)resp.size(), 0);

	delete pData;
}


/**************************************************************************************************/
/* 2. 构造与析构 (Constructor & Destructor)                                                      */
/**************************************************************************************************/

/**
 * @brief PLCCtrl类的构造函数，负责程序初始化
 */
PLCCtrl::PLCCtrl(void) {
	RedirectIOToConsole(); // 重定向IO到控制台，方便调试
	std::cout << "GetPLCData is Running!" << std::endl;

	initialize_value_pointer_map();// 1. 初始化内部代号 -> 变量地址的映射

	iniPath = exe_dir() + "\\setup.ini"; // 拼接INI文件路径
	{
		std::lock_guard<std::mutex> lk(cfgMutex);
		g_cfg = load_config_from_ini(iniPath); // 2. 加载INI配置, 解析[DEVICES]和[DEVICE_...]节, 填充激活的数据点列表
	}

	if (init_db()) { // 3. 连接数据库
		db_ping();
		resolve_all_mappings_at_startup();// 4. 根据激活的数据点列表, 向后端请求所有写入规则
	}



	// 启动配置刷新线程（每60秒重载一次INI）
	cfgThreadRun.store(true);
	cfgThread = std::thread(&PLCCtrl::config_refresher_loop, this);

	// 分别启动三个网络服务线程，并将其设置为后台运行(detach)
	std::thread TDrecvsocket(&PLCCtrl::socketSeverListenPort, this);
	std::thread TDrecvsocket485(&PLCCtrl::socketClientTo485Hnadler, this);
	std::thread TDrecvsocketPLC(&PLCCtrl::socketClientToPLCHnadler, this);
	TDrecvsocket.detach();
	TDrecvsocket485.detach();
	TDrecvsocketPLC.detach();
}

/**
 * @brief PLCCtrl类的析构函数，负责资源清理
 */
PLCCtrl::~PLCCtrl(void) {
	cfgThreadRun.store(false); // 通知配置刷新线程停止
	if (cfgThread.joinable()) cfgThread.join(); // 等待线程安全退出

	closesocket(clientSocket485); // 关闭套接字
	closesocket(clientSocketPLC);
	WSACleanup(); // 清理Winsock库
	close_db();   // 关闭数据库连接
}


/**************************************************************************************************/
/* 3. 核心后台线程 (Core Background Threads)                                                      */
/**************************************************************************************************/

/**
 * 【已修改】配置刷新线程的主循环函数
 * 增加了对新旧配置的比较，只为新增的数据点请求后端配置。
 */
void PLCCtrl::config_refresher_loop() {
	while (cfgThreadRun.load()) {
		// 等待60秒
		for (int i = 0; i < 60 && cfgThreadRun; ++i) Sleep(1000);
		if (!cfgThreadRun.load()) break;

		// 1. 保存旧的活动数据点列表的 "internal_key"
		std::set<std::string> old_keys;
		for (const auto& dp : m_active_data_points) {
			old_keys.insert(dp.internal_key);
		}

		// 2. 加载新的配置（这会覆盖 m_active_data_points）
		AdapterConfig fresh_config;
		{
			std::lock_guard<std::mutex> lk(cfgMutex); // 保护对 g_cfg 和 m_active_data_points 的写操作
			fresh_config = load_config_from_ini(iniPath);
			g_cfg = fresh_config;
		}

		// 3. 比较新旧列表，找出新增的数据点
		std::cout << "[CONFIG] INI file reloaded. Checking for new data points..." << std::endl;
		for (const auto& new_dp : m_active_data_points) {
			// 如果在旧的键集合中找不到新的键，说明是新增的
			if (old_keys.find(new_dp.internal_key) == old_keys.end()) {
				std::cout << "[CONFIG] New data point detected: " << new_dp.internal_key
					<< ". Resolving mapping from backend..." << std::endl;
				// 4. 只为新增的数据点请求后端配置
				resolve_mapping_from_backend(new_dp);
			}
		}
	}
}

/**
 * @brief 作为TCP服务器，监听指定端口，接收外部命令
 */
void PLCCtrl::socketSeverListenPort() {
	// 初始化Winsock
	WSADATA wsadata;
	if (WSAStartup(MAKEWORD(2, 2), &wsadata) != 0) {
		std::cout << "WSAStartup failed\n";
		return;
	}

	// 循环创建监听socket，包含重试机制
	int retries = 10;
	for (int i = 0; i < retries; ++i) {
		serverSocket = socket(AF_INET, SOCK_STREAM, 0);
		if (serverSocket == INVALID_SOCKET) {
			std::cout << "socket failed, retrying...\n";
			Sleep(500);
			continue;
		}
		sockaddr_in addr{};
		addr.sin_family = AF_INET;
		addr.sin_addr.s_addr = INADDR_ANY; // 绑定到所有网络接口
		addr.sin_port = htons(g_cfg.adapter.port);

		if (bind(serverSocket, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
			closesocket(serverSocket);
			std::cout << "bind failed, retrying...\n";
			Sleep(500);
			continue;
		}
		if (listen(serverSocket, SOMAXCONN) == SOCKET_ERROR) {
			closesocket(serverSocket);
			std::cout << "listen failed, retrying...\n";
			Sleep(500);
			continue;
		}
		std::cout << "Listening on " << g_cfg.adapter.port << "...\n";
		break;
	}

	if (serverSocket == INVALID_SOCKET) {
		std::cout << "Failed to start server after retries\n";
		return;
	}

	// 设置地址重用，避免端口被占用
	int yes = 1;
	setsockopt(serverSocket, SOL_SOCKET, SO_REUSEADDR, (char*)&yes, sizeof(yes));

	// 主循环，等待并处理客户端连接
	while (true) {
		sockaddr_in clientAddr;
		int clientAddrSize = sizeof(clientAddr);
		SOCKET c = accept(serverSocket, (sockaddr*)&clientAddr, &clientAddrSize);
		if (c == INVALID_SOCKET) continue;

		// 打印客户端连接信息
		char clientIp[INET_ADDRSTRLEN] = { 0 };
		inet_ntop(AF_INET, &clientAddr.sin_addr, clientIp, INET_ADDRSTRLEN);
		std::cout << "[INFO] Client connected from: " << clientIp << ":" << ntohs(clientAddr.sin_port) << std::endl;

		// 接收并处理来自客户端的数据
		std::string acc;
		char buf[1024];
		for (;;) {
			int n = recv(c, buf, sizeof(buf) - 1, 0);
			if (n <= 0) { closesocket(c); break; } // 连接断开或出错
			buf[n] = '\0';
			acc.append(buf, n);

			size_t pos = 0;
			for (;;) {
				// 使用换行符 '\n' 作为消息的定界符
				size_t newline_pos = acc.find('\n', pos);
				if (newline_pos == std::string::npos) break; // 未找到完整的消息
				std::string one = acc.substr(pos, newline_pos - pos);
				if (!one.empty()) {
					// 调用命令处理函数
					handle_command_line(one, c);
				}
				pos = newline_pos + 1;
			}
			if (pos) acc.erase(0, pos); // 清除已处理的数据
		}
	}
}

/**
 * @brief 作为TCP客户端，连接PLC并循环读取数据
 */
void PLCCtrl::socketClientToPLCHnadler()
{
	WSADATA wsadata;
	while (WSAStartup(MAKEWORD(2, 2), &wsadata) != 0) {
		std::cout << "WSAStartup failed" << std::endl;
		Sleep(100);
	}

	clientSocketPLC = INVALID_SOCKET;

	// 从配置中读取PLC的IP和端口
	std::string ip;
	int port;
	{
		std::lock_guard<std::mutex> lk(cfgMutex);
		ip = g_cfg.socket.ipPLC;
		port = g_cfg.socket.portPLC;
	}

	// 主循环，负责连接和数据读取
	while (true) {
		// 每次重连前，重置错误标志
		for (int i = 0; i < 8; i++) {
			press_ctrl[i].cllct_no_err = 0;
		}

		clientSocketPLC = socket(AF_INET, SOCK_STREAM, 0);
		if (clientSocketPLC == INVALID_SOCKET) {
			std::cout << "Socket creation failed: " << WSAGetLastError() << std::endl;
			Sleep(1000);
			continue;
		}

		sockaddr_in serverAddr;
		serverAddr.sin_family = AF_INET;
		serverAddr.sin_port = htons(port);
		inet_pton(AF_INET, ip.c_str(), &serverAddr.sin_addr);

		// 循环尝试连接服务器
		while (connect(clientSocketPLC, (sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
			Sleep(1000);
			continue;
		}

		std::cout << "Socket is connected to PLC" << std::endl;

		// 连接成功后，进入数据采集循环
		while (true) {
			{
				std::lock_guard<std::mutex> lk(plcMutex); // 获取PLC通信锁，避免与外部指令冲突
				if (getPLCData() < 0) // 调用函数读取数据
					break; // 如果读取失败，跳出内层循环去重连
			}
			Sleep(100); // 每100毫秒读取一次
		}
		closesocket(clientSocketPLC);
		std::cout << "Attempting to reconnect..." << std::endl;
		Sleep(1000); // 重连间隔
	}
	WSACleanup();
}

/**
 * @brief 作为TCP客户端，连接485设备并执行压力闭环控制
 */
void PLCCtrl::socketClientTo485Hnadler()
{
	WSADATA wsadata;
	while (WSAStartup(MAKEWORD(2, 2), &wsadata) != 0) {
		std::cout << "WSAStartup failed" << std::endl;
		Sleep(100);
	}

	clientSocket485 = INVALID_SOCKET;

	// 从配置中读取485设备的IP和端口
	std::string ip;
	int port;
	{
		std::lock_guard<std::mutex> lk(cfgMutex);
		ip = g_cfg.socket.ip485;
		port = g_cfg.socket.port485;
	};

	while (true) {
		clientSocket485 = socket(AF_INET, SOCK_STREAM, 0);
		if (clientSocket485 == INVALID_SOCKET) {
			std::cout << "Socket creation failed: " << WSAGetLastError() << std::endl;
			Sleep(1000);
			continue;
		}

		sockaddr_in serverAddr;
		serverAddr.sin_family = AF_INET;
		serverAddr.sin_port = htons(port);
		inet_pton(AF_INET, ip.c_str(), &serverAddr.sin_addr);

		// 循环尝试连接服务器
		while (connect(clientSocket485, (sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
			Sleep(1000);
			continue;
		}
		std::cout << "Socket is connected to 485 " << std::endl;

		// 连接成功后，进入压力控制主循环
		while (true) {
			for (int i = 0; i < sizeof(press_ctrl) / sizeof(press_ctrl[0]); i++) {
				// 检查是否需要进行调节：
				// 1. cllct_no_err: 当前压力值是有效读到的
				// 2. targetChanged: 目标压力值被外部指令改变过
				if (press_ctrl[i].cllct_no_err == 0 || press_ctrl[i].targetChanged == 0) {
					continue;
				}

				Sleep(10);
				if (AdjustValueOut(i) == -1) // 计算调节量并驱动阀门
					goto re_connect;
				Sleep(10);
				if (get_state(i) == -1) // 读取电机状态
					goto re_connect;

				// 检查压力是否已达到目标范围
				const double PRESSURE_TOLERANCE = 0.01; // 压力误差容忍范围, ±0.01 MPa
				{
					std::lock_guard<std::mutex> lk(dataMutex);
					// 仅在“调节中”状态时检查，避免重复设置
					if (press_ctrl[i].isTargetReached == 0) {
						double current_error = std::abs(press_ctrl[i].targetPress - press_ctrl[i].cllctPress);
						if (current_error <= PRESSURE_TOLERANCE) {
							// 误差在允许范围内，设置“目标达成”标志
							press_ctrl[i].isTargetReached = 1;
						}
					}
				}
			}
			Sleep(100);
		}

	re_connect: // 如果通信失败，跳转到这里进行重连
		closesocket(clientSocket485);
		std::cout << "Attempting to reconnect..." << std::endl;
		Sleep(1000);
	}

	WSACleanup();
}


/**************************************************************************************************/
/* 4. 网络与命令处理 (Networking & Command Handling)                                              */
/**************************************************************************************************/

/**
 * @brief 自定义的socket接收函数，带有200ms的超时
 * @param clientSocket 目标套接字
 * @param buffer 接收缓冲区
 * @param len 缓冲区长度
 * @return 接收到的字节数；0表示超时；小于0表示错误
 */
int PLCCtrl::my_recv_data(SOCKET clientSocket, char* buffer, int len)
{
	fd_set v;
	fd_set* readfds = &v;
	FD_ZERO(readfds);
	FD_SET(clientSocket, readfds);
	struct timeval timeout;
	//timeout.tv_sec = 0;
	//timeout.tv_usec = 500000;   //200ms
	timeout.tv_sec = 1;
	timeout.tv_usec = 0;
	int activity = select(clientSocket + 1, readfds, nullptr, nullptr, &timeout);

	if (activity > 0) {
		if (FD_ISSET(clientSocket, readfds)) {
			int recvResult = recv(clientSocket, buffer, len, 0);
			//std::cout << "recvResult (bytes received): " << recvResult << std::endl;
			return recvResult;
		}
	}
	return 0; // 超时或无数据
}

/**
 * @brief 处理单条命令行指令
 * @param line 从socket接收到的原始命令字符串
 * @param sock 用于响应的客户端socket
 * @return 0表示处理完成
 */
int PLCCtrl::handle_command_line(const std::string& line, SOCKET sock)
{
	std::string cmd = trim_copy(line);
	if (cmd.empty()) return 0;

	std::cout << "[pressctrl] recv raw: " << cmd << std::endl;

	std::string requestId = get_param(cmd, "requestId");
	if (requestId.empty()) requestId = "unknown";

	std::string orderStr = get_param(cmd, "order");
	int order = orderStr.empty() ? -1 : std::atoi(orderStr.c_str());

	// 解析通用参数 mode 和 timeout
	std::string modeStr = get_param(cmd, "mode");
	ExecutionMode mode = ExecutionMode::TARGET; // 默认 TARGET
	if (modeStr == "INSTANT") mode = ExecutionMode::INSTANT;
	else if (modeStr == "TARGET") mode = ExecutionMode::TARGET;
	else if (modeStr == "PROFILE") mode = ExecutionMode::PROFILE;

	std::string timeoutStr = get_param(cmd, "timeout");
	int timeout = timeoutStr.empty() ? 0 : std::atoi(timeoutStr.c_str());

	// 立即发送一个 "received" 状态的ACK响应
	{
		std::string ack = make_json(requestId, "received", "", "");
		ack.push_back('\n'); // 使用换行符作为消息结束符
		send(sock, ack.c_str(), (int)ack.size(), 0);
	}

	// 使用 switch 根据 order 分发到不同的处理逻辑
	switch (order) {
	case 1: { // 设置反应器目标压力 (软件闭环控制)
		// order=7&ch={channel}&target={value}
		// 这个指令不直接与PLC通信，而是更新内存中的目标值，
		// 由后台线程 socketClientTo485Hnadler 检测到变化后自动进行PID调节。
		std::string chStr = get_param(cmd, "ch");
		std::string targetStr = get_param(cmd, "target");

		int channel = std::atoi(chStr.c_str());
		float target_value = std::atof(targetStr.c_str());

		if (channel >= 1 && channel <= 8) {
			std::lock_guard<std::mutex> lk(dataMutex);
			press_ctrl[channel - 1].targetPress = target_value;
			press_ctrl[channel - 1].targetChanged = 1;  // 激活后台调节循环
			press_ctrl[channel - 1].isTargetReached = 0; // 重置“目标达成”标志
		}

		// 进入等待循环，直到isTargetReached变为1或超时(此处未实现超时)
		while (true) {
			{
				std::lock_guard<std::mutex> lk(dataMutex);
				if (press_ctrl[channel - 1].isTargetReached == 1) {
					break; // 目标已达成，跳出循环
				}
			}
			Sleep(200); // 每200毫秒检查一次状态
		}

		// 构建最终的 "finished" 响应
		std::string errorMsg = "";
		std::string data = "\"ch\":" + chStr + ",\"target\":" + targetStr;
		std::string resp = make_json(requestId, "finished", data, errorMsg);
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}

	case 2: {  // [修改] 反应通道分段加热 -> 原子化执行
		// order=8&ch={channel}&step={step}&temp={temperature}&time={minutes}
		std::string chStr = get_param(cmd, "ch");
		std::string stepStr = get_param(cmd, "step");
		std::string temStr = get_param(cmd, "temp");
		std::string timeStr = get_param(cmd, "time");
		int channel = std::atoi(chStr.c_str());
		int step = std::atoi(stepStr.c_str());
		int temp = std::atoi(temStr.c_str());
		int minutes = std::atoi(timeStr.c_str());

		int rc = 0;
		{
			std::lock_guard<std::mutex> lk(plcMutex);
			rc = set_reactor_step(channel, step, temp, minutes);
			// 智能启动
			if (rc >= 0) ensure_heating_started(channel);
		}

		if (rc < 0) {
			std::string resp = make_json(requestId, "finished", "", "Send failed");
			resp.push_back('\n');
			send(sock, resp.c_str(), (int)resp.size(), 0);
			break;
		}

		// [核心修改] 更新内存记录 & 追踪最大步数
		{
			std::lock_guard<std::mutex> lk(dataMutex);
			if (channel >= 1 && channel <= 8 && step >= 1 && step <= 10) {
				// 数组下标 0-9，对应 step 1-10
				tmp_ctrl[channel - 1].targetTmp[step - 1] = temp;
				tmp_ctrl[channel - 1].targetTime[step - 1] = minutes;

				// 更新该通道配置的最大段数
				if (step > tmp_ctrl[channel - 1].maxConfiguredStep) {
					tmp_ctrl[channel - 1].maxConfiguredStep = step;
				}
			}
		}

		// 如果未明确指定模式，但有时间设定，推断为 PROFILE 模式
		if (modeStr.empty() && minutes > 0) mode = ExecutionMode::PROFILE;

		// 使用新封装函数启动监控 (Type 0 = 反应通道)
		StartAsyncMonitor(sock, requestId, 0, (double)temp, channel, 0, step, minutes, mode, timeout);
		return 0; // 异步返回
	}
	case 3: {// 设置手动/自动模式
		// order=9&enable={1_or_0}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string enStr = get_param(cmd, "enable");
		int enable = std::atoi(enStr.c_str());
		int rc = set_manual_auto(enable);

		std::string data = "\"enable\":" + enStr;
		std::string resp = make_json(requestId, "finished", data, (rc < 0 ? "send/read failed" : ""));
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 4: {// 设置通道加热启动/停止
		// order=10&ch={channel}&start={1_or_0}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string chStr = get_param(cmd, "ch");
		std::string stStr = get_param(cmd, "start");

		int ch = std::atoi(chStr.c_str());
		int start = std::atoi(stStr.c_str());
		int rc = set_channel_start((unsigned char)ch, start);
		std::string data = "\"ch\":" + chStr + ",\"start\":" + stStr;
		std::string resp = make_json(requestId, "finished", data, (rc < 0 ? "send/read failed" : ""));
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 5: { // [修改] 保温箱控温 -> 闭环监控
		std::string idStr = get_param(cmd, "id");
		std::string tempStr = get_param(cmd, "temp");
		int id = std::atoi(idStr.c_str());
		int temp = std::atoi(tempStr.c_str());

		int rc = 0;
		{
			std::lock_guard<std::mutex> lk(plcMutex);
			rc = set_incubator_temp(id, temp);
		}

		if (rc < 0) { /* 错误处理略，参考上面 */ break; }

		// 使用新封装函数启动监控 (Type 1 = 保温箱)
		StartAsyncMonitor(sock, requestId, 1, (double)temp, 0, id, 0, 0, mode, timeout);
		return 0;
	}
	case 6: { // 设置反应报警温度
		// order=12&ch={channel}&temp={temperature}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string chStr = get_param(cmd, "ch");
		std::string tempStr = get_param(cmd, "temp");
		int ch = std::atoi(chStr.c_str());
		int temp = std::atoi(tempStr.c_str());
		int rc = set_reaction_alarm_temp(ch, temp);

		std::string data = "\"ch\":" + chStr + ",\"temp\":" + tempStr;
		std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 7: { // 设置热阱报警温度
		// order=13&ch={channel}&temp={temperature}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string chStr = get_param(cmd, "ch");
		std::string tempStr = get_param(cmd, "temp");
		int ch = std::atoi(chStr.c_str());
		int temp = std::atoi(tempStr.c_str());
		int rc = set_heatsink_alarm_temp(ch, temp);

		std::string data = "\"ch\":" + chStr + ",\"temp\":" + tempStr;
		std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 8: { // 设置保温箱报警温度
		// order=14&id={incubator_id}&temp={temperature}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string idStr = get_param(cmd, "id");
		std::string tempStr = get_param(cmd, "temp");
		int id = std::atoi(idStr.c_str());
		int temp = std::atoi(tempStr.c_str());
		int rc = set_incubator_alarm_temp(id, temp);

		std::string data = "\"id\":" + idStr + ",\"temp\":" + tempStr;
		std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 9: { // 设置PLC使能位
		// order=15&ch={enable_id}&status={1_or_0}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string idStr = get_param(cmd, "ch");
		std::string statusStr = get_param(cmd, "status");
		int id = std::atoi(idStr.c_str());
		int status = std::atoi(statusStr.c_str());
		int rc = set_plc_enable(id, status);

		std::string data = "\"id\":" + idStr + ",\"status\":" + statusStr;
		std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 10: { // [修改] 气体流量 -> 闭环监控
		std::string chStr = get_param(cmd, "ch");
		std::string segStr = get_param(cmd, "seg");
		std::string flowStr = get_param(cmd, "flow");
		int ch = std::atoi(chStr.c_str());
		int seg = std::atoi(segStr.c_str());
		int flow = std::atoi(flowStr.c_str());

		int rc = 0;
		{
			std::lock_guard<std::mutex> lk(plcMutex);
			rc = set_gas_flow(ch, seg, flow);
		}

		if (rc < 0) { /* 错误处理略 */ break; }

		// 使用新封装函数启动监控 (Type 4 = 气体流量)
		// 注意：复用 step 参数传递 segment (seg)
		StartAsyncMonitor(sock, requestId, 4, (double)flow, ch, 0, seg, 0, mode, timeout);
		return 0;
	}
		   //case 17: { // 获取实时气体流量
		   //	// order=17&ch={channel}&seg={segment}
		   //	std::lock_guard<std::mutex> lk(plcMutex);
		   //	std::string chStr = get_param(cmd, "ch");
		   //	std::string segStr = get_param(cmd, "seg");
		   //	int ch = std::atoi(chStr.c_str());
		   //	int seg = std::atoi(segStr.c_str());
		   //	float flow = 0.0f;
		   //	int rc = get_realtime_gas_flow(ch, seg, flow);

		   //	std::string data = "\"ch\":" + chStr + ",\"seg\":" + segStr + ",\"flow\":" + std::to_string(flow);
		   //	std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		   //	resp.push_back('\n');
		   //	send(sock, resp.c_str(), (int)resp.size(), 0);
		   //	break;
		   //}
				  // --- [优化点] 读指令改为读内存 ---

	case 11: { // [优化] 获取实时气体流量 (读内存)
		std::string chStr = get_param(cmd, "ch");
		std::string segStr = get_param(cmd, "seg");
		int ch = std::atoi(chStr.c_str());
		int seg = std::atoi(segStr.c_str());
		float flow = 0.0f;

		{
			std::lock_guard<std::mutex> lk(dataMutex); // 锁 dataMutex
			if (ch >= 1 && ch <= 8 && seg >= 1 && seg <= 2) {
				flow = gas_flow[ch - 1][seg - 1];
			}
		}

		// 只要索引合法就认为成功，无需检查 rc
		std::string data = "\"ch\":" + chStr + ",\"seg\":" + segStr + ",\"flow\":" + std::to_string(flow);
		std::string resp = make_json(requestId, "finished", data, "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
		   //case 18: { // 获取色谱伴热温度
		   //	// order=18
		   //	std::lock_guard<std::mutex> lk(plcMutex);
		   //	float temp = 0.0f;
		   //	int rc = get_chromatography_temp(temp);

		   //	std::string data = "\"temp\":" + std::to_string(temp);
		   //	std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		   //	resp.push_back('\n');
		   //	send(sock, resp.c_str(), (int)resp.size(), 0);
		   //	break;
		   //}
	case 12: { // [优化] 获取色谱伴热温度 (读内存)
		float temp = 0.0f;
		{
			std::lock_guard<std::mutex> lk(dataMutex);
			temp = chromatography_temp;
		}

		std::string data = "\"temp\":" + std::to_string(temp);
		std::string resp = make_json(requestId, "finished", data, "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 13: { // 设置色谱报警温度
		// order=19&temp={temperature}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string tempStr = get_param(cmd, "temp");
		int temp = std::atoi(tempStr.c_str());
		int rc = set_chromatography_alarm_temp(temp);

		std::string data = "\"temp\":" + tempStr;
		std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 14: { // [修改] 色谱控温 -> 闭环监控
		std::string tempStr = get_param(cmd, "temp");
		int temp = std::atoi(tempStr.c_str());

		int rc = 0;
		{
			std::lock_guard<std::mutex> lk(plcMutex);
			rc = set_chromatography_temp(temp);
		}

		if (rc < 0) { /* 错误处理略 */ break; }

		// 使用新封装函数启动监控 (Type 2 = 色谱)
		StartAsyncMonitor(sock, requestId, 2, (double)temp, 0, 0, 0, 0, mode, timeout);
		return 0;
	}
		   //case 21: { // 获取热阱温度
		   //	// order=21&ch={channel}
		   //	std::lock_guard<std::mutex> lk(plcMutex);
		   //	std::string chStr = get_param(cmd, "ch");
		   //	int ch = std::atoi(chStr.c_str());
		   //	float temp = 0.0f;
		   //	int rc = get_heatsink_temp(ch, temp);

		   //	std::string data = "\"ch\":" + chStr + ",\"temp\":" + std::to_string(temp);
		   //	std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		   //	resp.push_back('\n');
		   //	send(sock, resp.c_str(), (int)resp.size(), 0);
		   //	break;
		   //}
	case 15: { // [优化] 获取热阱温度 (读内存)
		std::string chStr = get_param(cmd, "ch");
		int ch = std::atoi(chStr.c_str());
		float temp = 0.0f;

		{
			std::lock_guard<std::mutex> lk(dataMutex);
			if (ch >= 1 && ch <= 8) {
				temp = heatsink_temps[ch - 1];
			}
		}

		std::string data = "\"ch\":" + chStr + ",\"temp\":" + std::to_string(temp);
		std::string resp = make_json(requestId, "finished", data, "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
		   //case 22: { // 获取保温箱温度
		   //	// order=22&id={sensor_id}
		   //	std::lock_guard<std::mutex> lk(plcMutex);
		   //	std::string idStr = get_param(cmd, "id");
		   //	int id = std::atoi(idStr.c_str());
		   //	float temp = 0.0f;
		   //	int rc = get_incubator_temp(id, temp);

		   //	std::string data = "\"id\":" + idStr + ",\"temp\":" + std::to_string(temp);
		   //	std::string resp = make_json(requestId, "finished", data, rc < 0 ? "failed" : "");
		   //	resp.push_back('\n');
		   //	send(sock, resp.c_str(), (int)resp.size(), 0);
		   //	break;
		   //}
	case 16: { // [优化] 获取保温箱温度 (读内存)
		// order=22&id={sensor_id}
		std::string idStr = get_param(cmd, "id");
		int id = std::atoi(idStr.c_str());
		float temp = 0.0f;

		{
			// 直接读取后台线程更新好的内存数据，无需网络 IO，速度极快
			std::lock_guard<std::mutex> lk(dataMutex);
			if (id >= 1 && id <= 2) {
				// 注意：根据 PLCCtrl.h，incubator_temps 是 float[2]
				temp = incubator_temps[id - 1];
			}
		}

		std::string data = "\"id\":" + idStr + ",\"temp\":" + std::to_string(temp);
		std::string resp = make_json(requestId, "finished", data, "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 17: { // [修改] 热阱控温 -> 闭环监控
		std::string chStr = get_param(cmd, "ch");
		std::string tempStr = get_param(cmd, "temp");
		int ch = std::atoi(chStr.c_str());
		int temp = std::atoi(tempStr.c_str());

		int rc = 0;
		{
			std::lock_guard<std::mutex> lk(plcMutex);
			rc = set_heatsink_temp(ch, temp);
		}

		if (rc < 0) { /* 错误处理略 */ break; }

		// 使用新封装函数启动监控 (Type 3 = 热阱)
		StartAsyncMonitor(sock, requestId, 3, (double)temp, ch, 0, 0, 0, mode, timeout);
		return 0;
	}
	case 18: { // 设置报警消音
		// order=24&status={1_or_0}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string stStr = get_param(cmd, "status");
		int status = std::atoi(stStr.c_str());
		int rc = set_alarm_mute(status);

		std::string data = "\"status\":" + stStr;
		std::string resp = make_json(requestId, "finished", data, (rc < 0 ? "send/read failed" : ""));
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 19: { // 设置一氧化碳阀状态
		// order=25&status={1_or_0}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string stStr = get_param(cmd, "status");
		int status = std::atoi(stStr.c_str());
		int rc = set_co_valve(status);

		std::string data = "\"status\":" + stStr;
		std::string resp = make_json(requestId, "finished", data, (rc < 0 ? "send/read failed" : ""));
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 20: { // 设置氢气阀状态
		// order=26&status={1_or_0}
		std::lock_guard<std::mutex> lk(plcMutex);
		std::string stStr = get_param(cmd, "status");
		int status = std::atoi(stStr.c_str());
		int rc = set_h2_valve(status);

		std::string data = "\"status\":" + stStr;
		std::string resp = make_json(requestId, "finished", data, (rc < 0 ? "send/read failed" : ""));
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	case 21: { // 保持反应通道温度 -> 自动追加新段并闭环监控
		// order=21&ch={channel}&temp={target_temp}
		std::string chStr = get_param(cmd, "ch");
		std::string tempStr = get_param(cmd, "temp");
		int channel = std::atoi(chStr.c_str());
		int temp = std::atoi(tempStr.c_str());
		int nextStep = 1;

		{// 1. 获取下一段
			std::lock_guard<std::mutex> lk(dataMutex);
			if (channel >= 1 && channel <= 8) {
				nextStep = tmp_ctrl[channel - 1].maxConfiguredStep + 1;
			}
		}
		if (nextStep > 10) {
			std::string resp = make_json(requestId, "failed", "", "Segments full (Max 10)");
			resp.push_back('\n');
			send(sock, resp.c_str(), (int)resp.size(), 0);
			break;
		}
		int time_infinity = 9999;
		int rc = 0;
		{// 2. 写入 PLC
			std::lock_guard<std::mutex> lk(plcMutex);
			rc = set_reactor_step(channel, nextStep, temp, time_infinity);
			if (rc >= 0) ensure_heating_started(channel);
		}

		if (rc < 0) {
			std::string resp = make_json(requestId, "failed", "", "PLC write failed");
			resp.push_back('\n');
			send(sock, resp.c_str(), (int)resp.size(), 0);
			break;
		}
		{// 3. 更新内存状态
			std::lock_guard<std::mutex> lk(dataMutex);
			if (channel >= 1 && channel <= 8) {
				tmp_ctrl[channel - 1].targetTmp[nextStep - 1] = temp;
				tmp_ctrl[channel - 1].targetTime[nextStep - 1] = time_infinity;
				tmp_ctrl[channel - 1].maxConfiguredStep = nextStep;
			}
		}

		// 4. 启动监控 (使用 INSTANT 模式)
		StartAsyncMonitor(sock, requestId, 0, (double)temp, channel, 0, nextStep, time_infinity, ExecutionMode::INSTANT, timeout);
		return 0;
	}
	default: {  // 未知order，返回一个echo响应
		std::string data = "\"echo\":\"" + cmd + "\"";
		std::string resp = make_json(requestId, "unknown", data, "");
		resp.push_back('\n');
		send(sock, resp.c_str(), (int)resp.size(), 0);
		break;
	}
	}
	return 0;
}


/**************************************************************************************************/
/* 5. 数据库交互 (Database Interaction)                                                           */
/**************************************************************************************************/

/**
 * @brief 检查PostgreSQL操作结果是否成功
 * @param r PGresult指针
 * @return 成功返回true，否则返回false
 */
static bool pg_ok(PGresult* r) {
	if (!r) return false;
	auto s = PQresultStatus(r);
	return s == PGRES_COMMAND_OK || s == PGRES_TUPLES_OK;
}

/**
 * @brief 初始化到PostgreSQL数据库的连接
 * @return 连接成功返回true，否则返回false
 */
bool PLCCtrl::init_db() {
	std::string conninfo = "host=" + g_cfg.db.host +
		" port=" + std::to_string(g_cfg.db.port) +
		" dbname=" + g_cfg.db.dbname +
		" user=" + g_cfg.db.user +
		" password=" + g_cfg.db.password;
	pgConn = PQconnectdb(conninfo.c_str());
	if (PQstatus(pgConn) != CONNECTION_OK) {
		std::cerr << "[Postgres] connect failed: " << PQerrorMessage(pgConn) << std::endl;
		return false;
	}
	std::cout << "[Postgres] connected ok" << std::endl;
	return true;
}

/**
 * @brief 关闭数据库连接
 */
void PLCCtrl::close_db() {
	if (pgConn) { PQfinish(pgConn); pgConn = nullptr; }
}

/**
 * @brief 对数据库执行一次ping操作 (SELECT 1)，检查连接是否存活
 * @return 连接正常返回true，否则返回false
 */
bool PLCCtrl::db_ping() {
	if (!pgConn) { std::cerr << "[Postgres] no connection handle\n"; return false; }
	PGresult* r = PQexec(pgConn, "SELECT 1");
	if (!r || PQresultStatus(r) != PGRES_TUPLES_OK) {
		std::cerr << "[Postgres] ping failed: " << PQerrorMessage(pgConn) << std::endl;
		if (r) PQclear(r);
		return false;
	}
	PQclear(r);
	std::cout << "[Postgres] ping ok, server_version=" << PQserverVersion(pgConn)
		<< ", db=" << PQdb(pgConn) << ", user=" << PQuser(pgConn) << "\n";
	return true;
}

/**
 * 【已修改】使用动态配置和预备语句，将单个读数高效地插入数据库。
 * 增加了在缓存未命中时，主动从后端重新获取配置的逻辑，提高了健壮性。
 */
void PLCCtrl::insert_reading(const DataPointConfig& dp, double value) {
	// 1. 安全检查：确保数据库连接有效。
	if (!pgConn || PQstatus(pgConn) != CONNECTION_OK) {
		std::cerr << "[DB] Connection lost. Skipping insertion for " << dp.internal_key << std::endl;
		return;
	}

	// 2. 从缓存中查找此数据点对应的写入规则。
	auto it = m_write_config_cache.find(dp.internal_key);
	if (it == m_write_config_cache.end()) {
		// 【修改点】缓存未命中，可能是启动时API不可用，或配置是热重载的。
		// 尝试从后端实时获取一次配置。
		std::cerr << "[DB] Write config for '" << dp.internal_key << "' not in cache. Attempting to resolve from backend now..." << std::endl;
		if (!resolve_mapping_from_backend(dp)) {
			// 如果实时获取也失败，则本次跳过，等待下一次。
			std::cerr << "[DB] Failed to resolve mapping on-the-fly for '" << dp.internal_key << "'. Skipping insertion." << std::endl;
			return;
		}
		// 再次查找，这次应该能找到了
		it = m_write_config_cache.find(dp.internal_key);
		if (it == m_write_config_cache.end()) {
			return; // 极端情况，还是没找到，直接返回
		}
	}

	const WriteConfig& config = it->second;

	// 3. 性能优化：使用 PostgreSQL 的“预备语句 (Prepared Statement)”。
	std::string stmt_name = "ins_" + dp.internal_key;
	if (m_prepared_statements.find(stmt_name) == m_prepared_statements.end()) {
		PGresult* r = PQprepare(pgConn, stmt_name.c_str(), config.sqlTemplate.c_str(), 1, nullptr);
		if (!pg_ok(r)) {
			std::cerr << "[DB] Prepare failed for " << stmt_name << ": " << PQresultErrorMessage(r) << std::endl;
			PQclear(r);
			return;
		}
		PQclear(r);
		m_prepared_statements.insert(stmt_name);
	}

	// 准备要插入的实际参数值
	std::string val_str = std::to_string(value);
	const char* vals[1] = { val_str.c_str() };

	// 执行预备好的语句
	PGresult* r = PQexecPrepared(pgConn, stmt_name.c_str(), 1, vals, nullptr, nullptr, 0);
	if (!pg_ok(r)) {
		std::cerr << "[DB] Insert failed for " << dp.internal_key << ": " << PQresultErrorMessage(r) << std::endl;
	}
	PQclear(r);
}

/**
 * @brief 智能的批量数据上报函数。
 * 该函数作为数据上报的“调度中心”，遍历所有在INI中被激活的数据点，
 * 从内存中获取它们的最新值，并调用通用的插入函数写入数据库。
 */
void PLCCtrl::insert_all_readings_batch() {
	// 1. 线程安全：对共享的数据区加锁，防止与数据采集线程冲突。
	// 当这个函数在读取 press_ctrl 和 tmp_ctrl 等变量时，
	// getPLCData 线程将无法写入，确保了数据的一致性。
	std::lock_guard<std::mutex> lk(dataMutex);

	// 2. 遍历由 INI 配置文件生成的所有“活动数据点”工作列表。
	for (const auto& dp : m_active_data_points) {

		// 3. 关键步骤：通过“内部代号”查找其在C++代码中对应的物理变量的内存地址。
		// m_value_pointers 是一个 std::map<std::string, double*>，
		// 它在初始化时建立了 "C1_TEMP" -> &tmp_ctrl[0].cllctTmp 这样的映射。
		auto it_ptr = m_value_pointers.find(dp.internal_key);

		if (it_ptr != m_value_pointers.end()) {
			// 4. 如果找到了映射关系，则解引用指针，从内存中直接获取最新的数据值。
			double current_value = *(it_ptr->second);

			// 5. 调用通用的插入函数，将数据点的业务信息(dp)和物理值(current_value)
			// 一同传递过去，完成最终的数据库写入。
			insert_reading(dp, current_value);
		}
	}
}


/**************************************************************************************************/
/* 6. 动态配置解析 (Dynamic Configuration Resolution)                                             */
/**************************************************************************************************/

/**
 * @brief 从INI文件加载所有配置项。
 * 此函数负责解析INI，并根据[DEVICES]和[DEVICE_*]节
 * 构建一个包含所有活动数据点的详细工作列表(m_active_data_points)。
 * @param path INI文件路径
 * @return 填充好的基础AdapterConfig对象
 */
AdapterConfig PLCCtrl::load_config_from_ini(const std::string& path) {
	AdapterConfig cfg;

	// [ADAPTER] 节
	cfg.adapter.name = read_ini_stringA(path.c_str(), "ADAPTER", "NAME");
	cfg.adapter.host = read_ini_stringA(path.c_str(), "ADAPTER", "HOST");
	if (cfg.adapter.host.empty()) cfg.adapter.host = detect_local_ipv4();
	cfg.adapter.port = read_ini_intA(path.c_str(), "ADAPTER", "PORT", 4000);
	cfg.adapter.desc = read_ini_stringA(path.c_str(), "ADAPTER", "DESC");
	cfg.adapter.cmdTpl = read_ini_stringA(path.c_str(), "ADAPTER", "CMD_TPL");
	cfg.adapter.api_host = read_ini_stringA(path.c_str(), "ADAPTER", "API_HOST", "http://localhost:8084");

	// [SOCKET] 节
	cfg.socket.ip485 = read_ini_stringA(path.c_str(), "SOCKET", "485_IP", "192.168.0.201");
	cfg.socket.port485 = read_ini_intA(path.c_str(), "SOCKET", "485_PORT", 26);
	cfg.socket.ipPLC = read_ini_stringA(path.c_str(), "SOCKET", "PLC_IP", "192.168.0.10");
	cfg.socket.portPLC = read_ini_intA(path.c_str(), "SOCKET", "PLC_PORT", 502);

	// [DB] 节
	cfg.db.host = read_ini_stringA(path.c_str(), "DB", "host", "localhost");
	cfg.db.port = read_ini_intA(path.c_str(), "DB", "port", 5432);
	cfg.db.dbname = read_ini_stringA(path.c_str(), "DB", "dbname", "testdb");
	cfg.db.user = read_ini_stringA(path.c_str(), "DB", "user", "postgres");
	cfg.db.password = read_ini_stringA(path.c_str(), "DB", "password", "password");

	// --- 核心逻辑: 解析 [DEVICES] 和 [DEVICE_*] 节，生成详细工作列表 ---
	m_active_data_points.clear(); // 每次加载都清空旧的工作列表

	// 1. 从 [DEVICES] 节中读取 "LIST" 键
	std::string deviceListStr = read_ini_stringA(path.c_str(), "DEVICES", "LIST", "");
	if (deviceListStr.empty()) {
		std::cout << "[CONFIG] No devices listed in [DEVICES] section. Active data point list is empty." << std::endl;
		return cfg;
	}
	// 2. 分割设备名
	std::vector<std::string> deviceNames = split_string(deviceListStr, ',');
	// 3. 遍历每个设备名，读取其专属配置节
	for (const auto& rawDeviceName : deviceNames) {
		std::string deviceName = trim_string(rawDeviceName);
		if (deviceName.empty()) continue;
		std::string sectionName = "DEVICE_" + deviceName;
		char sectionBuffer[4096] = { 0 };
		DWORD bytesRead = GetPrivateProfileSectionA(sectionName.c_str(), sectionBuffer, sizeof(sectionBuffer), path.c_str());
		if (bytesRead == 0) {
			std::cerr << "[CONFIG WARN] Section [" << sectionName << "] is listed but not found or is empty." << std::endl;
			continue;
		}
		std::istringstream iss(std::string(sectionBuffer, bytesRead));
		std::string line;
		// 4. 解析节中的每一行 key = value
		while (std::getline(iss, line, '\0')) {
			if (line.empty()) continue;
			size_t eq_pos = line.find('=');
			if (eq_pos != std::string::npos) {
				// 提取“内部代号”和“数据类型”
				std::string key = trim_string(line.substr(0, eq_pos));
				std::string val = trim_string(line.substr(eq_pos + 1));
				if (key.empty() || val.empty()) continue;
				// 创建一个完整的数据点配置对象并填充
				DataPointConfig dp;
				dp.internal_key = key;
				dp.deviceName = deviceName;
				dp.dataType = val;
				// 将配置好的数据点添加到工作列表中
				m_active_data_points.push_back(dp);
			}
		}
	}

	std::cout << "[CONFIG] Loaded and activated " << m_active_data_points.size() << " data points from INI config." << std::endl;
	return cfg;
}

/**
 * @brief 通过HTTP请求从后端API获取单个设备/数据类型的数据库写入配置
 * @param deviceName 设备名
 * @param dataType 数据类型
 * @return 成功获取并解析返回true，否则返回false
 */
bool PLCCtrl::resolve_mapping_from_backend(const DataPointConfig& dp) {
	CURL* curl = curl_easy_init();
	if (!curl) return false;

	std::string response;
	// 修正API端点和参数名
	std::string url = g_cfg.adapter.api_host + "/api/adapter/resolve-table?deviceName=" + dp.deviceName + "&dataType=" + dp.dataType;

	curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
	curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
	curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
	curl_easy_setopt(curl, CURLOPT_TIMEOUT, 10L);

	CURLcode res = curl_easy_perform(curl);
	long http_code = 0;
	curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
	curl_easy_cleanup(curl);

	if (res != CURLE_OK || http_code != 200) {
		std::cerr << "[API] Request failed for " << dp.internal_key << ". HTTP: " << http_code << ", Error: " << curl_easy_strerror(res) << std::endl;
		return false;
	}

	try {
		json j = json::parse(response);
		WriteConfig config;
		// 修正JSON键名以匹配后端
		config.targetTable = j.at("targetTable").get<std::string>();
		config.sqlTemplate = j.at("sqlTemplate").get<std::string>();

		// 使用internal_key作为缓存的主键
		m_write_config_cache[dp.internal_key] = config;
		std::cout << "[API] Resolved config for " << dp.internal_key << " (" << dp.deviceName << "/" << dp.dataType << ")" << std::endl;
		return true;
	}
	catch (const json::exception& e) {
		std::cerr << "[API] JSON parse failed for " << dp.internal_key << ": " << e.what() << std::endl;
		return false;
	}
}

/**
 * @brief 在程序启动时，遍历INI中配置的所有设备，并从后端API获取它们的写入配置
 */
void PLCCtrl::resolve_all_mappings_at_startup() {
	std::cout << "[INFO] Resolving all active mappings from backend..." << std::endl;
	for (const auto& dp : m_active_data_points) {
		resolve_mapping_from_backend(dp);
	}
}


/**************************************************************************************************/
/* 7. PLC/Modbus TCP 通信 (PLC/Modbus TCP Communication)                                          */
/**************************************************************************************************/

/**
 * @brief 批量读取多个寄存器 (Function Code 0x03)
 * @param startAddr 起始地址
 * @param count 寄存器数量
 * @param outBuf 输出缓冲区
 * @return 0 成功, -1 失败
 */
int PLCCtrl::read_multi_registers(uint16_t startAddr, uint16_t count, uint16_t* outBuf)
{
	struct cmd_t {
#pragma pack(1)
		uint16_t transactionId;//事务 ID，设为 1（通常应递增，但此处固定也能用）
		uint16_t protocolId;//协议 ID，Modbus TCP 固定为 0
		uint16_t length;//后续数据的字节长度。对于读指令，后面跟着 UnitId(1) + FuncCode(1) + Addr(2) + Quant(2) = 6 字节
		uint8_t  unitId;//单元 ID，设为 1（从站地址）
		uint8_t  functionCode;//0x03，表示读保持寄存器
		uint16_t startingAddress;//起始寄存器地址
		uint16_t quantity;//要读取的寄存器数量 (count)
	} cmd = { 0 };

	cmd.transactionId = byteConversion((uint16_t)0x0001);
	cmd.protocolId = 0x0000;
	cmd.length = byteConversion((uint16_t)0x0006);
	cmd.unitId = 0x01;
	cmd.functionCode = 0x03;
	cmd.startingAddress = byteConversion(startAddr);
	cmd.quantity = byteConversion(count);

	if (send(clientSocketPLC, (char*)(&cmd), sizeof(cmd), 0) == SOCKET_ERROR) return -1;

	// 计算预期响应长度: Header(9) + Bytes(1) + Data(count*2)
	// Header: TransID(2)+ProtID(2)+Len(2)+Unit(1)+Func(1) + ByteCount(1)
	int expected_bytes = 9 + count * 2;
	// 缓冲区稍微大一点
	std::vector<char> buffer(expected_bytes + 10, 0);

	int len = my_recv_data(clientSocketPLC, buffer.data(), expected_bytes);

	if (len < 9) return -1; // 头部都不完整
	if (buffer[7] != 0x03) return -1; // 功能码不对

	// 字节数检查
	uint8_t byteCount = (uint8_t)buffer[8];
	if (byteCount != count * 2) return -1;

	// 解析数据 (注意大小端转换)
	for (int i = 0; i < count; i++) {
		uint16_t val_net = *(uint16_t*)(&buffer[9 + i * 2]);
		// 假设本地是小端，PLC是大端，需要转换
		// 如果 byteConversion 是互转的，直接调用
		outBuf[i] = byteConversion(val_net);
	}

	return 0;
}

/**
 * @brief 解析 PLC 镜像数据到业务结构体
 * @note 此函数从 m_plc_mirror 数组中取数，填充 struct
 */
void PLCCtrl::parse_plc_data()
{
	// 1. 采集反应温度 (地址 0-7)
	for (int i = 0; i < 8; ++i) {
		// 防止数组越界
		if (i < 297) {
			tmp_ctrl[i].cllctTmp = static_cast<double>(static_cast<int16_t>(m_plc_mirror[i])) / 10.0;
		}
	}

	// 2. 采集反应压力 (地址 11, 14... 步长3)
	for (int i = 0; i < 8; ++i) {
		int addr = 11 + i * 3;
		if (addr < 297) {
			press_ctrl[i].cllctPress = static_cast<double>(static_cast<int16_t>(m_plc_mirror[addr])) / 100.0;
		}
	}

	// 3. 采集气体流量 (地址 251-266)
	// 通道1: 251,252; 通道2: 253,254 ...
	for (int i = 0; i < 8; ++i) {
		for (int j = 0; j < 2; ++j) {
			int addr = 251 + (i * 2) + j;
			if (addr < 297) {
				gas_flow[i][j] = static_cast<double>(static_cast<int16_t>(m_plc_mirror[addr]));
			}
		}
	}

	// 4. 采集色谱温度 (地址 276)
	if (276 < 297) {
		chromatography_temp = static_cast<double>(static_cast<int16_t>(m_plc_mirror[276])) / 10.0;
	}

	// 5. 采集热阱温度 (地址 279-286)
	for (int i = 0; i < 8; ++i) {
		int addr = 279 + i;
		if (addr < 297) {
			heatsink_temps[i] = static_cast<double>(static_cast<int16_t>(m_plc_mirror[addr])) / 10.0;
		}
	}

	// 6. 采集保温箱温度 (地址 287, 288)
	for (int i = 0; i < 2; ++i) {
		int addr = 287 + i;
		if (addr < 297) {
			incubator_temps[i] = static_cast<double>(static_cast<int16_t>(m_plc_mirror[addr])) / 10.0;
		}
	}
}

///**
// * @brief 从PLC读取所有温度和压力数据
// * @return 0表示成功，-1表示通信错误
// */
//int PLCCtrl::getPLCData()
//{
//	std::lock_guard<std::mutex> lock(dataMutex);
//	int16_t raw_value = 0;
//	// 1. 采集反应温度 (地址 0-7)
//	for (int i = 0; i < 8; ++i) {
//		//int16_t temp_val = 0;
//		read_single_register(i, raw_value); // 读取寄存器
//		tmp_ctrl[i].cllctTmp = static_cast<double>(raw_value) / 10.0; // 需要除以10
//	}
//
//	// 2. 采集反应压力 (地址 11, 14, ..., 32)
//	for (int i = 0; i < 8; ++i) {
//		//int16_t press_val = 0;
//		read_single_register(11 + i * 3, raw_value);
//		press_ctrl[i].cllctPress = static_cast<double>(raw_value) / 100.0; // 需要除以100
//	}
//
//	// 3. 采集气体流量 (地址 251-256...)
//	for (int i = 0; i < 8; ++i) {
//		for (int j = 0; j < 2; ++j) {
//			uint16_t addr = 251 + i * 2 + j;
//			read_single_register(addr, raw_value);
//			gas_flow[i][j]= static_cast<float>(raw_value);
//		}
//	}
//
//	// 4. 采集色谱温度 (地址 276)
//	read_single_register(276, raw_value);
//	chromatography_temp = static_cast<float>(raw_value) / 10.0;
//
//	// 5. 采集热阱温度 (地址 279-286)
//	for (int i = 0; i < 8; ++i) {
//		uint16_t addr = 279 + i;
//		int result = read_single_register(addr, raw_value);
//		heatsink_temps[i] = static_cast<float>(raw_value) / 10.0;
//	}
//
//	// 6. 采集保温箱温度 (地址 287, 288)
//	for (int i = 0; i < 2; ++i) {
//		uint16_t addr = 287 + i;
//		int result = read_single_register(addr, raw_value);
//		incubator_temps[i] = static_cast<float>(raw_value) / 10.0;
//	}
//
//	// 7. 所有数据采集完毕后，调用批量上报
//	insert_all_readings_batch();
//	return 0;
//}

/**
 * @brief [修改] 从PLC读取所有数据 (优化版)
 * 使用分块读取代替单点读取，大幅提升效率
 */
int PLCCtrl::getPLCData()
{
	//std::lock_guard<std::mutex> lk(plcMutex); // 锁网络   //外部调用者已经加锁了

	// 分块读取 0-296
	// --- 第一阶段：标准分块读取 (0 ~ 269) ---
	// 0 到 269 共 270 个寄存器，刚好可以被 30 整除 (9次)
	for (int start_addr = 0; start_addr < 270; start_addr += 30) {

		int ret = read_multi_registers((uint16_t)start_addr, 30, &m_plc_mirror[start_addr]);

		if (ret < 0) {
			std::cout << "[PLC] Loop read failed at: " << start_addr << ". Retrying..." << std::endl;
			return -1;
		}

		// 每次读完休息 50ms，防止 PLC 处理不过来
		Sleep(50);
	}

	// --- 第二阶段：(270 ~ 296) ---
	int ret_tail = read_multi_registers(270, 27, &m_plc_mirror[270]);

	if (ret_tail < 0) {
		std::cout << "[PLC] Tail read failed at 270. Retrying..." << std::endl;
		return -1;
	}

	// 读完最后一块给下一次大循环留出空隙
	Sleep(50);

	// 解析数据并存入结构体
	{
		std::lock_guard<std::mutex> dlk(dataMutex); // 锁内存数据结构
		parse_plc_data();
	}

	// 上报数据库
	insert_all_readings_batch();

	//// =============== 【新增功能】 ===============
	//// 尝试保存数据到 CSV (Excel)
	//// 放心调用，函数内部有时间判断，只会每 5 秒写一次
	//saveDataToCSV();
	//// ===========================================

	return 0;
}


/**
 * @brief 向PLC写入单个保持寄存器 (功能码 0x06)
 * @param addr 寄存器地址
 * @param val 要写入的值
 * @return 1表示成功，-1表示通信错误，0表示无响应
 */
int PLCCtrl::write_single_reg(uint16_t addr, uint16_t val)
{
	// 构造Modbus TCP写单个寄存器请求帧
	struct cmd_t {
#pragma pack(1)
		uint16_t transactionId;
		uint16_t protocolId;
		uint16_t length;
		uint8_t  unitId;
		uint8_t  functionCode;
		uint16_t startingAddress;
		uint16_t value;
	} cmd = { 0 };

	cmd.transactionId = 0x0000;
	cmd.protocolId = 0x0000;
	cmd.length = byteConversion((uint16_t)0x0006); // 长度固定为6
	cmd.unitId = 0x01;
	cmd.functionCode = 0x06;
	cmd.startingAddress = byteConversion(addr);
	cmd.value = byteConversion(val);

	send(clientSocketPLC, (char*)(&cmd), sizeof(cmd), 0);

	// 等待并检查响应
	char buffer[1024] = { 0 }; // 稍微加大一点缓冲区
	int len = my_recv_data(clientSocketPLC, buffer, sizeof(buffer));
	if (len < 0) {
		std::cout << "write_single_reg: socket error" << std::endl;
		return -1;
	}
	else if (len == 0) {
		std::cout << "write_single_reg: PLC no response (Timeout)" << std::endl;
		return 0;
	}
	// --- [核心修改开始] 增加协议校验逻辑 ---

	// 1. 长度校验：Modbus TCP 写响应至少 12 字节 (6字节头 + 1单元 + 1功能 + 2地址 + 2值)
	// 异常响应通常是 9 字节 (6字节头 + 1单元 + 1异常功能码 + 1异常码)
	if (len < 9) {
		std::cout << "write_single_reg: response too short" << std::endl;
		return -1;
	}

	// --- 解析头部关键字段 ---
	uint8_t respFunctionCode = (uint8_t)buffer[7];
	uint16_t respTransactionId = *(uint16_t*)buffer; // 注意：这里是网络序，不需要转就能跟 cmd.transactionId 比

	// --- 步骤一：检查事务 ID (防止串包) ---
	// 如果你使用了动态 ID，这里必须对比。如果是固定 0，也要比对。
	if (respTransactionId != cmd.transactionId) {
		std::cout << "write_single_reg: Transaction ID mismatch!" << std::endl;
		return -1;
	}

	// --- 步骤二：检查异常响应 (PLC 拒绝写入) ---
	if (respFunctionCode == (0x06 | 0x80)) {
		uint8_t exceptionCode = (uint8_t)buffer[8];
		std::cout << "write_single_reg: PLC Exception Error! Code: " << (int)exceptionCode << std::endl;
		return -1; // 明确失败
	}

	// --- 步骤三：检查功能码是否正确 ---
	if (respFunctionCode != 0x06) {
		std::cout << "write_single_reg: Unknown function code " << (int)respFunctionCode << std::endl;
		return -1;
	}

	// --- 步骤四：回显校验 (Echo Check) ---
	// 检查响应长度是否足够包含地址和值 (12字节)
	if (len < 12) {
		std::cout << "write_single_reg: success response length error" << std::endl;
		return -1;
	}

	// 关键点：直接对比内存。
	// 响应的 [8-9] 字节应等于 cmd.startingAddress
	// 响应的 [10-11] 字节应等于 cmd.value
	// 因为 cmd 里的成员已经是大端序（通过 byteConversion 转换过），
	// Buffer 里收到的也是大端序，所以可以直接字节对比，不需要再转一次。

	uint16_t respAddr = *(uint16_t*)(buffer + 8);
	uint16_t respVal = *(uint16_t*)(buffer + 10);

	if (respAddr != cmd.startingAddress) {
		std::cout << "write_single_reg: Address mismatch! Sent: " << addr << " RecvAddrBytes: " << respAddr << std::endl;
		return -1;
	}

	if (respVal != cmd.value) {
		std::cout << "write_single_reg: Value mismatch! Sent: " << val << " RecvValBytes: " << respVal << std::endl;
		return -1; // 哪怕写入了，但值不对，也算失败
	}

	return 1; // 完美匹配，确认写入成功
}

/**
 * @brief 从PLC读取单个保持寄存器 (功能码 0x03)
 * @param addr 寄存器地址
 * @param val_out 用于接收读取到的值的引用
 * @return 0表示成功, -1表示通信错误, -2表示响应格式错误
 */
int PLCCtrl::read_single_register(uint16_t addr, int16_t& val_out)
{
	// 构造Modbus TCP读单个寄存器请求帧
	struct cmd_t {
#pragma pack(1)
		uint16_t transactionId;
		uint16_t protocolId;
		uint16_t length;
		uint8_t  unitId;
		uint8_t  functionCode;
		uint16_t startingAddress;
		uint16_t quantity;
	} cmd = { 0 };

	cmd.transactionId = byteConversion((uint16_t)0x0001);
	cmd.protocolId = 0x0000;
	cmd.length = byteConversion((uint16_t)0x0006);
	cmd.unitId = 0x01;
	cmd.functionCode = 0x03;
	cmd.startingAddress = byteConversion(addr);
	cmd.quantity = byteConversion((uint16_t)0x0001);

	send(clientSocketPLC, (char*)(&cmd), sizeof(cmd), 0);

	char buffer[100] = { 0 };
	int len = my_recv_data(clientSocketPLC, buffer, sizeof(buffer));
	if (len < 0) {
		std::cout << "read_single_register: my_recv_data err " << len << std::endl;
		return -1;
	}
	else if (len == 0) {
		std::cout << "read_single_register: no responce" << std::endl;
		return -1;
	}

	// 验证并解析响应数据
	if (len >= 9 && buffer[7] == 0x03 && buffer[8] == 0x02) {
		uint16_t raw_val = *(uint16_t*)(buffer + 9);
		val_out = byteConversion(raw_val);
		return 0; // 成功
	}

	return -2; // 响应格式错误
}


/**************************************************************************************************/
/* 8. 485/Modbus RTU 通信 (485/Modbus RTU Communication)                                          */
/**************************************************************************************************/

/**
 * @brief 发送压力调节指令到485设备
 * @param ch 通道(设备地址-1)
 * @param dir 方向
 * @param speed 速度 (rpm)
 * @param pluse 脉冲数
 * @return 0
 */
int PLCCtrl::sendPresscommand(unsigned char ch, unsigned char dir, double speed, uint32_t pluse)
{
	// 构造Modbus RTU写多个寄存器(0x10)的指令帧
	struct cmd_t {
#pragma pack(1)
		unsigned char devAddr;
		unsigned char fun;
		uint16_t regAddr;
		uint16_t regNum;
		unsigned char byteNum;
		unsigned char dir;
		unsigned char dltSpeed;
		uint16_t speed;
		uint32_t pluse;
		uint8_t abs;
		uint8_t sync;
		uint16_t crc;
	} cmd = { 0 };

	uint16_t speed_val = speed;
	if (speed < 2 && speed > 0) speed = 2;
	cmd.devAddr = ch + 1;
	cmd.fun = 0x10;
	cmd.regAddr = byteConversion((uint16_t)0x00FD);
	cmd.regNum = byteConversion((uint16_t)0x0005);
	cmd.byteNum = 0x0005 * 2;
	cmd.dir = dir;
	cmd.dltSpeed = 0;
	cmd.speed = byteConversion(speed_val);
	cmd.pluse = byteConversion(pluse);
	cmd.abs = 0;
	cmd.sync = 0;
	cmd.crc = calc_nb_modbus_crc((uint8_t*)(&cmd), sizeof(cmd) - 2); // 计算CRC
	send(clientSocket485, (char*)(&cmd), sizeof(cmd), 0);
	return 0;
}

/**
 * @brief 从485设备读取状态信息
 * @param ch 通道(设备地址-1)
 * @return 0表示成功，-1表示通信失败
 */
int PLCCtrl::get_state(unsigned char ch)
{
	// 构造Modbus RTU读输入寄存器(0x04)指令帧
	struct cmd_t {
#pragma pack(1)
		unsigned char devAddr;
		unsigned char fun;
		uint16_t regAddr;
		uint16_t regNum;
		uint16_t crc;
	} cmd = { 0 };
	cmd.devAddr = ch + 1;
	cmd.fun = 0x04;
	cmd.regAddr = byteConversion((uint16_t)0x0043);
	cmd.regNum = byteConversion((uint16_t)0x0010);
	cmd.crc = calc_nb_modbus_crc((uint8_t*)(&cmd), sizeof(cmd) - 2);

	send(clientSocket485, (char*)(&cmd), sizeof(cmd), 0);

	char buffer[1024] = { 0 };
	int len = my_recv_data(clientSocket485, buffer, sizeof(buffer));
	if (len < 0) {
		std::cout << "485 socket err " << len << std::endl;
		return -1;
	}
	else if (len > 0) {
		if (len == 37) { // 检查响应长度是否符合预期
			unsigned char val = (unsigned char)buffer[34];
			if (val & 8) { // 解析报警位
				press_ctrl[ch].moter_alarm = 1;
			}
			else {
				press_ctrl[ch].moter_alarm = 0;
			}
		}
	}
	else {
		press_ctrl[ch].moter_no_responce++; // 无响应计数器
	}

	return 0;
}

/**
 * @brief 发送停止指令到485设备
 * @param ch 通道
 * @return 0
 */
int PLCCtrl::stop(unsigned char ch)
{
	char cmd[] = { ch, 0x10, 0x00, 0xFE, 0x00, 0x01, 0x02, 0x98, 0x00 };
	send(clientSocket485, (char*)(&cmd), sizeof(cmd), 0);
	return 0;
}

/**
 * @brief 清除485设备的报警
 * @param ch 通道
 * @return 1表示成功，-1表示通信失败，0表示无响应
 */
int PLCCtrl::clear_alarm(unsigned char ch)
{
	struct cmd_t {
#pragma pack(1)
		unsigned char devAddr;
		unsigned char fun;
		uint16_t regAddr;
		uint16_t regVal;
		uint16_t crc;
	} cmd = { 0 };

	cmd.devAddr = ch + 1;
	cmd.fun = 0x06; // 写单个寄存器
	cmd.regAddr = byteConversion((uint16_t)0x000E);
	cmd.crc = calc_nb_modbus_crc((uint8_t*)(&cmd), sizeof(cmd) - 2);
	send(clientSocket485, (char*)(&cmd), sizeof(cmd), 0);

	char buffer[1024] = { 0 };
	int len = my_recv_data(clientSocket485, buffer, sizeof(buffer));
	if (len < 0) {
		std::cout << "485 socket err " << len << std::endl;
		return -1;
	}
	else if (len > 0) {
		press_ctrl[ch].moter_no_responce = 0;
		return 1;
	}
	else {
		press_ctrl[ch].moter_no_responce++;
	}

	return 0;
}

/**
 * @brief 设置485设备的参数
 * @param ch 通道
 * @return 1表示成功，-1表示通信失败，0表示无响应
 */
int PLCCtrl::set_para(unsigned char ch)
{
	struct cmd_t {
#pragma pack(1)
		unsigned char devAddr;
		unsigned char fun;
		uint16_t regAddr;
		uint16_t regNum;
		unsigned char byteNum;
		uint16_t reg[15];
		uint16_t crc;
	};

	// 这是一个预设的参数帧
	uint8_t modbus_frame[] = {
		0x01, 0x10, 0x00, 0x48, 0x00, 0x0F, 0x1E, 0xD1,
		0x01, 0x19, 0x02, 0x02, 0x02, 0x00, 0x10, 0x01,
		0x00, 0x03, 0xE8, 0x09, 0x60, 0x13, 0x88, 0x05,
		0x07, 0x01, 0x03, 0x01, 0x01, 0x00, 0x08, 0x08,
		0x98, 0x07, 0xD0, 0x00, 0x03, 0x5A, 0x70
	};

	struct cmd_t* cmd = (struct cmd_t*)modbus_frame;
	cmd->devAddr = ch + 1; // 设置目标设备地址
	cmd->reg[12] = byteConversion((uint16_t)2200); // 修改其中一个参数
	cmd->crc = calc_nb_modbus_crc((uint8_t*)(cmd), sizeof(struct cmd_t) - 2); // 重新计算CRC
	send(clientSocket485, (char*)(cmd), sizeof(struct cmd_t), 0);

	char buffer[1024] = { 0 };
	int len = my_recv_data(clientSocket485, buffer, sizeof(buffer));
	if (len < 0) {
		std::cout << "485 socket err " << len << std::endl;
		return -1;
	}
	else if (len > 0) {
		press_ctrl[ch].moter_no_responce = 0;
		return 1;
	}
	else {
		press_ctrl[ch].moter_no_responce++;
	}

	return 0;
}


/**************************************************************************************************/
/* 9. 业务逻辑封装 (Business Logic Wrappers)                                                      */
/**************************************************************************************************/

/**
 * @brief PID控制算法核心，计算气动阀调节量并发送指令
 * @param ch 通道号
 * @return 0或1表示成功，-1表示通信失败
 */
int PLCCtrl::AdjustValueOut(unsigned char ch)
{
	double err;
	{
		std::lock_guard<std::mutex> lk(dataMutex); // 保护数据读写
		err = press_ctrl[ch].targetPress - press_ctrl[ch].cllctPress;
		// 更新历史误差记录（用于微分D）
		for (int i = 0; i < sizeof(press_ctrl[ch].lastErr) / sizeof(press_ctrl[ch].lastErr[0]) - 1; i++) {
			press_ctrl[ch].lastErr[i] = press_ctrl[ch].lastErr[i + 1];
		}
		press_ctrl[ch].lastErr[sizeof(press_ctrl[ch].lastErr) / sizeof(press_ctrl[ch].lastErr[0]) - 1] = err;
	}

	// PID 计算
	double P = err * kp; // 比例
	press_ctrl[ch].I += err * ki; // 积分
	// 此处可以添加积分饱和限制
	double D = (err - press_ctrl[ch].lastErr[0]) * kd; // 微分

	double revolutionsPerS = P + D + press_ctrl[ch].I; // 计算输出速度 (转/秒)
	std::cout << "revolutionsPerS: " << revolutionsPerS << std::endl;

	unsigned dir = revolutionsPerS < 0; // 确定方向
	if (revolutionsPerS < 0) {
		revolutionsPerS = -revolutionsPerS; // 取绝对值
	}
	if (revolutionsPerS > 100) { // 速度上限
		revolutionsPerS = 100;
	}

	// 将速度转换为脉冲数并发送指令
	double cycle_in_1second = revolutionsPerS / 60.0;
	unsigned int pulse = cycle_in_1second * 3200;
	sendPresscommand(ch, dir, revolutionsPerS, revolutionsPerS * 3200 / 60);

	char buffer[1024] = { 0 };
	int len = my_recv_data(clientSocket485, buffer, sizeof(buffer));
	if (len < 0) {
		std::cout << "485 socket err " << len << std::endl;
		return -1;
	}
	else if (len > 0) {
		press_ctrl[ch].moter_no_responce = 0;
		return 1;
	}
	else {
		press_ctrl[ch].moter_no_responce++;
	}

	return 0;
}

/**
 * @brief 设置保温箱控温值
 * @param incubator_id 保温箱编号 (1 或 2)
 * @param temperature 目标温度值 (例如 500 表示 50.0°C)
 * @note PLC地址: 1号->210, 2号->211
 */
int PLCCtrl::set_incubator_temp(int incubator_id, int temperature)
{
	if (incubator_id < 1 || incubator_id > 2) return -1;
	uint16_t addr = 210 + (incubator_id - 1);
	uint16_t val = static_cast<uint16_t>(temperature);
	return write_single_reg(addr, val);
}

/**
 * @brief 设置通道反应报警温度
 * @param channel 通道号 (1-8)
 * @param temperature 报警温度值
 * @note PLC地址: 通道1->212, ... , 通道8->219
 */
int PLCCtrl::set_reaction_alarm_temp(int channel, int temperature)
{
	if (channel < 1 || channel > 8) return -1;
	uint16_t addr = 212 + (channel - 1);
	uint16_t val = static_cast<uint16_t>(temperature);
	return write_single_reg(addr, val);
}

/**
 * @brief 设置热阱报警温度
 * @param channel 通道号 (1-8)
 * @param temperature 报警温度值
 * @note PLC地址不连续，需要分情况讨论
 */
int PLCCtrl::set_heatsink_alarm_temp(int channel, int temperature)
{
	if (channel < 1 || channel > 8) return -1;
	uint16_t addr;
	if (channel <= 4)
		addr = 220 + (channel - 1);
	else if (channel == 5)
		addr = 289;
	else if (channel == 6)
		addr = 290;
	else if (channel == 7)
		addr = 292;
	else if (channel == 8)
		addr = 291;
	else
		return -1;
	uint16_t val = static_cast<uint16_t>(temperature);
	return write_single_reg(addr, val);
}

/**
 * @brief 设置保温箱报警温度
 * @param incubator_id 保温箱编号 (1 或 2)
 * @param temperature 报警温度值
 * @note PLC地址: 1号->224, 2号->225
 */
int PLCCtrl::set_incubator_alarm_temp(int incubator_id, int temperature)
{
	if (incubator_id < 1 || incubator_id > 2) return -1;
	uint16_t addr = 224 + (incubator_id - 1);
	uint16_t val = static_cast<uint16_t>(temperature);
	return write_single_reg(addr, val);
}

/**
 * @brief 设置PLC使能位
 * @param enable_id 使能项编号 (1-9)
 * @param status 状态 (1=使能, 0=禁止)
 * @note PLC地址: 使能1->226, ..., 使能9->234
 */
int PLCCtrl::set_plc_enable(int enable_id, int status)
{
	if (enable_id < 1 || enable_id > 9) return -1;
	uint16_t addr = 226 + (enable_id - 1);
	uint16_t val = (status ? 1 : 0);
	return write_single_reg(addr, val);
}

/**
 * @brief 设置气体流量
 * @param channel 通道号 (1-8)
 * @param segment 流量段编号 (1 或 2)
 * @param flow_rate 流量设定值
 * @note PLC地址: 通道1第1段->235, ..., 通道8第2段->250
 */
int PLCCtrl::set_gas_flow(int channel, int segment, int flow_rate)
{
	if (channel < 1 || channel > 8 || segment < 1 || segment > 2) return -1;
	uint16_t addr = 235 + (channel - 1) * 2 + (segment - 1);
	uint16_t val = static_cast<uint16_t>(flow_rate);
	return write_single_reg(addr, val);
}

/**
 * @brief 获取实时气体流量
 * @param channel 通道号 (1-8)
 * @param segment 流量段编号 (1 或 2)
 * @param flow_value 用于接收流量值的引用
 * @return 0表示成功, 其他为失败
 * @note PLC地址: 通道1第1段->251, ..., 通道8第2段->266
 */
int PLCCtrl::get_realtime_gas_flow(int channel, int segment, float& flow_value)
{
	if (channel < 1 || channel > 8 || segment < 1 || segment > 2) return -1;
	uint16_t addr = 251 + (channel - 1) * 2 + (segment - 1);

	int16_t raw_value = 0;
	int result = read_single_register(addr, raw_value);
	if (result == 0) {
		flow_value = static_cast<float>(raw_value);
	}
	return result;
}

/**
 * @brief 读取色谱伴热温度
 * @param temp_value 用于接收温度值的引用
 * @return 0表示成功, 其他为失败
 * @note PLC地址: 276
 */
int PLCCtrl::get_chromatography_temp(float& temp_value)
{
	int16_t raw_value = 0;
	int result = read_single_register(276, raw_value);
	if (result == 0) {
		temp_value = static_cast<double>(raw_value) / 10.0; // 温度值需要除以10
	}
	return result;
}

/**
 * @brief 设置色谱报警温度
 * @param temperature 报警温度值
 * @note PLC地址: 277
 */
int PLCCtrl::set_chromatography_alarm_temp(int temperature)
{
	uint16_t val = static_cast<uint16_t>(temperature);
	return write_single_reg(277, val);
}

/**
 * @brief 设置色谱温度（控温）
 * @param temperature 目标温度值
 * @note PLC地址: 278
 */
int PLCCtrl::set_chromatography_temp(int temperature)
{
	uint16_t val = static_cast<uint16_t>(temperature);
	return write_single_reg(278, val);
}

/**
 * @brief 读取热阱温度
 * @param heatsink_id 热阱编号 (1-8)
 * @param temp_value 用于接收温度值的引用
 * @return 0表示成功, 其他为失败
 * @note PLC地址: 热阱1->279, ..., 热阱8->286
 */
int PLCCtrl::get_heatsink_temp(int heatsink_id, float& temp_value)
{
	if (heatsink_id < 1 || heatsink_id > 8) return -1;
	uint16_t addr = 279 + (heatsink_id - 1);

	int16_t raw_value = 0;
	int result = read_single_register(addr, raw_value);
	if (result == 0) {
		temp_value = static_cast<double>(raw_value) / 10.0;
	}
	return result;
}

/**
 * @brief 获取保温箱温度
 * @param sensor_id 传感器编号 (1-12)
 * @param temp_value 用于接收温度值的引用
 * @return 0表示成功, 其他为失败
 * @note PLC地址: 传感器1->287, ..., 传感器12->288
 */
int PLCCtrl::get_incubator_temp(int sensor_id, float& temp_value)
{
	if (sensor_id < 1 || sensor_id > 12) return -1;
	uint16_t addr = 287 + (sensor_id - 1);

	int16_t raw_value = 0;
	int result = read_single_register(addr, raw_value);
	if (result == 0) {
		temp_value = static_cast<double>(raw_value) / 10.0;
	}
	return result;
}

/**
 * @brief 设置热阱控温
 * @param channel 通道号 (1-8)
 * @param temperature 目标温度
 * @note PLC地址: 通道1->54, 通道2->76, ..., 步进22
 */
int PLCCtrl::set_heatsink_temp(int channel, int temperature)
{
	if (channel < 1 || channel > 8) return -1;

	// 地址计算: 基地址 + (通道号 - 1) * 步长
	const uint16_t BASE_ADDR = 54;
	const uint16_t STRIDE = 22;
	uint16_t addr = BASE_ADDR + (channel - 1) * STRIDE;

	uint16_t val = static_cast<uint16_t>(temperature);
	return write_single_reg(addr, val);
}

/**
 * @brief 设置反应器程序控温的某一个步骤
 * @param channel 通道号 (1-8)
 * @param step 步骤号 (1-10)
 * @param temperature 目标温度
 * @param minutes 持续时间（分钟）
 * @return 1
 */
int PLCCtrl::set_reactor_step(int channel, int step, int temperature, int minutes)
{
	// PLC寄存器地址映射规则
	const uint16_t CH1_BASE_ADDR = 34;
	const uint16_t REGS_PER_STEP = 2;
	const uint16_t STEPS_PER_CH = 10;
	const uint16_t HEAT_SINK_REGS = 1;
	const uint16_t GAP_REGS = 1;
	const uint16_t CH_TOTAL_STRIDE = (STEPS_PER_CH * REGS_PER_STEP) + HEAT_SINK_REGS + GAP_REGS; // 22

	// 计算目标通道的基地址
	uint16_t channel_base_addr = static_cast<uint16_t>(CH1_BASE_ADDR + (channel - 1) * CH_TOTAL_STRIDE);

	// 计算该步骤的温度和时间寄存器地址
	uint16_t tempAddr = static_cast<uint16_t>(channel_base_addr + (step - 1) * REGS_PER_STEP);
	uint16_t timeAddr = static_cast<uint16_t>(tempAddr + 1);

	// 依次写入温度和时间
	uint16_t temp_u16 = static_cast<uint16_t>(temperature);
	uint16_t time_u16 = static_cast<uint16_t>(minutes);

	write_single_reg(tempAddr, temp_u16);
	Sleep(10); // 短暂延时，确保PLC有时间处理
	write_single_reg(timeAddr, time_u16);

	return 1;
}

/**
 * @brief 设置手动/自动模式切换
 * @param enableWrite 0=手动, 1=自动
 * @return 0
 */
int PLCCtrl::set_manual_auto(int enableWrite)
{
	static const uint16_t REG_WRITE_GATE = 267; // 允许/禁止写入的寄存器地址
	uint16_t val = (enableWrite ? 1 : 0);
	write_single_reg(REG_WRITE_GATE, val);
	return 0;
}

/**
 * @brief 启动或停止指定通道的加热程序
 * @param ch 通道号 (1-8)
 * @param start 1=启动, 0=停止
 * @return 0
 */
int PLCCtrl::set_channel_start(unsigned char ch, int start)
{
	static const uint16_t REG_START_BASE = 268; // 通道1启动地址=268, ...
	uint16_t addr = (uint16_t)(REG_START_BASE + (ch - 1));
	uint16_t val = (start ? 1 : 0);
	write_single_reg(addr, val);
	return 0;
}

/**
 * @brief 设置报警消音
 * @param status 状态 (1=消音, 0=取消消音)
 */
int PLCCtrl::set_alarm_mute(int status)
{
	const uint16_t addr = 293;
	uint16_t val = (status ? 1 : 0);
	write_single_reg(addr, val);
	return 0;
}

/**
 * @brief 设置一氧化碳阀状态
 * @param status 状态 (1=打开, 0=关闭)
 */
int PLCCtrl::set_co_valve(int status)
{
	const uint16_t REG_CO_VALVE = 294;
	uint16_t val = (status ? 1 : 0);
	return write_single_reg(REG_CO_VALVE, val);
}

/**
 * @brief 设置氢气阀状态
 * @param status 状态 (1=打开, 0=关闭)
 */
int PLCCtrl::set_h2_valve(int status)
{
	const uint16_t REG_H2_VALVE = 295;
	uint16_t val = (status ? 1 : 0);
	return write_single_reg(REG_H2_VALVE, val);
}

/**
 * @brief 判断是否已经启动加热：确保通道始终处于启动状态
 */
int PLCCtrl::ensure_heating_started(unsigned char ch)
{
	static const uint16_t REG_START_BASE = 268;
	uint16_t addr = (uint16_t)(REG_START_BASE + (ch - 1));
	int16_t current_status = 0;

	// 先读，避免重复写
	int read_res = read_single_register(addr, current_status);
	if (read_res == 0 && current_status == 1) return 0;

	return set_channel_start(ch, 1);
}

//void PLCCtrl::saveDataToCSV()
//{
//	// 1. 获取当前系统时间
//	time_t now = time(0);
//
//	// 2. 检查时间间隔：如果距离上次保存不到 5 秒，直接退出
//	if (now - last_save_time < 5) {
//		return;
//	}
//	last_save_time = now; // 更新保存时间
//
//	// 3. 以“追加模式”打开文件
//	std::ofstream file("PLC_Data_Vertical.csv", std::ios::app);
//
//	if (!file.is_open()) {
//		std::cout << "[Error] Cannot open CSV file for writing!" << std::endl;
//		return;
//	}
//
//	// 4. 如果文件是空的，写入表头
//	file.seekp(0, std::ios::end);
//	if (file.tellp() == 0) {
//		file << "Time,Address,Value\n"; // 表头
//	}
//
//	// 5. 【核心修复】使用 localtime_s 替代 localtime
//	struct tm ltm;
//	// localtime_s 的第一个参数是结构体指针，第二个是时间戳指针
//	localtime_s(&ltm, &now);
//
//	char timeStr[32];
//	// 注意：这里使用 ltm.tm_year (用点号) 而不是 ltm->tm_year (用箭头)，因为 ltm 现在是结构体实体
//	strftime(timeStr, 32, "%Y-%m-%d %H:%M:%S", &ltm);
//
//	// 6. 遍历所有寄存器写入
//	for (int i = 0; i <= 296; i++) {
//		file << timeStr << ","          // 第一列：时间
//			<< "Addr_" << i << ","     // 第二列：寄存器地址
//			<< m_plc_mirror[i]         // 第三列：数值
//			<< "\n";                   // 换行
//	}
//
//	file.close();
//}