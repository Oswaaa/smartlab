package com.smartlab.agent.operator;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话仓库。
 *
 * <p>两级过期时间，刻意不同：
 * <ul>
 *   <li><b>会话 30 分钟</b>：用户可能问一句、想一会儿再问，不能太短。</li>
 *   <li><b>待确认动作 5 分钟</b>：确认窗口必须短。悬着半小时的"加热到 500 度"再被点确认，
 *       现场情况早就变了，所以宁可让它过期、重新走一遍预演。</li>
 * </ul>
 *
 * <p>进程内存储，重启即清空。这是有意的：重启后设备真实状态可能已变，残留的待确认动作反而是隐患。
 */
@Service
public class OperatorSessionStore {

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration PENDING_TTL = Duration.ofMinutes(5);
    private static final int MAX_SESSIONS = 200;

    private final Map<String, OperatorSession> sessions = new ConcurrentHashMap<>();

    public OperatorSession getOrCreate(String sessionId) {
        evictExpired();
        String id = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId.trim();
        OperatorSession session = sessions.computeIfAbsent(id, OperatorSession::new);
        session.touch();
        return session;
    }

    public OperatorSession find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        OperatorSession session = sessions.get(sessionId.trim());
        if (session == null) return null;
        if (session.expired(SESSION_TTL)) {
            sessions.remove(sessionId.trim());
            return null;
        }
        session.touch();
        return session;
    }

    /** 待确认动作是否还有效。过期则顺带清掉。 */
    public boolean pendingAlive(OperatorSession session) {
        PendingAction pending = session == null ? null : session.pending();
        if (pending == null) return false;
        long elapsed = Duration.between(pending.createdAt(), java.time.Instant.now()).toMillis();
        if (elapsed > PENDING_TTL.toMillis()) {
            session.clearPending();
            return false;
        }
        return true;
    }

    public long pendingTtlSeconds() {
        return PENDING_TTL.getSeconds();
    }

    public void evictExpired() {
        if (sessions.size() <= MAX_SESSIONS) {
            sessions.entrySet().removeIf(entry -> entry.getValue().expired(SESSION_TTL));
            return;
        }
        // 超过上限时，按最后活跃时间淘汰最旧的一批
        sessions.entrySet().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .limit(Math.max(0, sessions.size() - MAX_SESSIONS / 2L))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(sessions::remove);
        sessions.entrySet().removeIf(entry -> entry.getValue().expired(SESSION_TTL));
    }
}
