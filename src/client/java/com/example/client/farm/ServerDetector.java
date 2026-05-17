package com.example.client.farm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayDeque;
import java.util.Deque;

public class ServerDetector {
    private static final int MAX_RECENT_TEXTS = 50;
    private static final long TEXT_KEEP_MS = 5000;

    private final Deque<TimedText> recentTexts = new ArrayDeque<>();

    // 모드 내부 테스트 메시지는 서버 판별에 노이즈가 되므로 수집에서 제외한다.
    public boolean shouldCaptureText(String text) {
        if (text == null || text.isBlank()) return false;
        if (text.contains("[서버 타입 인식 테스트]")) return false;
        if (text.contains("[RAW TAB TEXT]")) return false;
        if (text.contains("[상자 등록]")) return false;
        if (text.contains("[상자 목록]")) return false;
        if (text.contains("[상자 초기화]")) return false;
        if (text.contains("[인벤토리 검사]")) return false;
        if (text.contains("[상자 화면 검사]")) return false;
        if (text.contains("[자동 투입]")) return false;
        if (text.contains("[DryRun]")) return false;
        if (text.contains("결과:")) return false;
        if (text.contains("설명:")) return false;
        if (text.contains("탐지된 라인:")) return false;
        return true;
    }

    public void addRecentText(String text) {
        recentTexts.addLast(new TimedText(text, System.currentTimeMillis()));

        while (recentTexts.size() > MAX_RECENT_TEXTS) {
            recentTexts.removeFirst();
        }

        System.out.println("[SERVER TEXT] " + text);
    }

    public void removeOldTexts() {
        long now = System.currentTimeMillis();

        while (!recentTexts.isEmpty()) {
            TimedText first = recentTexts.peekFirst();

            if (now - first.timeMs <= TEXT_KEEP_MS) {
                break;
            }

            recentTexts.removeFirst();
        }
    }

    public ServerType getCurrentServerType(Minecraft client) {
        // 탭 라인 판별 우선 + 최근 텍스트 보조 판별 순서로 서버 타입을 결정한다.
        String rawTabText = getRawTabText(client);
        String matchedLine = findMyChannelLine(rawTabText, client.player.getGameProfile().getName());
        String recentText = getRecentTextJoined();

        return detectServerType(matchedLine, recentText);
    }

    public String getRawTabText(Minecraft client) {
        if (client.getConnection() == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
            if (info.getTabListDisplayName() != null) {
                builder.append(info.getTabListDisplayName().getString()).append("\n");
            }

            if (info.getProfile() != null) {
                builder.append(info.getProfile().getName()).append("\n");
            }
        }

        return builder.toString();
    }

    public String findMyChannelLine(String rawText, String myName) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String[] lines = rawText.split("\n");

        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            if (!line.contains(myName)) continue;
            if (!line.contains("채널")) continue;

            return line.trim();
        }

        return "";
    }

    public ServerType detectServerType(String matchedLine, String recentText) {
        ServerType lineResult = detectFromChannelLine(matchedLine);

        if (lineResult != ServerType.UNKNOWN) {
            return lineResult;
        }

        ServerType recentResult = detectFromRecentText(recentText);

        if (recentResult != ServerType.UNKNOWN) {
            return recentResult;
        }

        return ServerType.UNKNOWN;
    }

    private ServerType detectFromChannelLine(String line) {
        if (line == null || line.isBlank()) {
            return ServerType.UNKNOWN;
        }

        String lower = line.toLowerCase();

        if (line.contains("마을") || lower.contains("town")) return ServerType.TOWN;
        if (line.contains("스폰") || lower.contains("spawn")) return ServerType.SPAWN;
        if (line.contains("아일랜드") || lower.contains("island")) return ServerType.ISLAND;
        if (line.contains("야생") || lower.contains("wild")) return ServerType.WILD;

        return ServerType.UNKNOWN;
    }

    private ServerType detectFromRecentText(String text) {
        if (text == null || text.isBlank()) {
            return ServerType.UNKNOWN;
        }

        if (text.contains("잠수 포인트")) {
            return ServerType.AFK;
        }

        if (text.contains("확률형 아이템")) {
            return ServerType.LOBBY;
        }

        return ServerType.UNKNOWN;
    }

    public String getServerColor(ServerType type) {
        return switch (type) {
            case TOWN -> "§a";
            case SPAWN -> "§e";
            case ISLAND -> "§b";
            case WILD -> "§2";
            case AFK -> "§d";
            case LOBBY -> "§6";
            case UNKNOWN -> "§c";
        };
    }

    public String getServerDescription(ServerType type) {
        return switch (type) {
            case TOWN -> "마을 서버";
            case SPAWN -> "스폰 서버";
            case ISLAND -> "아일랜드 서버";
            case WILD -> "야생 서버";
            case AFK -> "잠수 서버";
            case LOBBY -> "로비 서버";
            case UNKNOWN -> "알 수 없음";
        };
    }

    public String getRecentTextJoined() {
        removeOldTexts();

        StringBuilder builder = new StringBuilder();

        for (TimedText timedText : recentTexts) {
            builder.append(timedText.text).append("\n");
        }

        return builder.toString();
    }

    private static class TimedText {
        private final String text;
        private final long timeMs;

        private TimedText(String text, long timeMs) {
            this.text = text;
            this.timeMs = timeMs;
        }
    }
}
