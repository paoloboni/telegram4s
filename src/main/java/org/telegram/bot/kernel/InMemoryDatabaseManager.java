package org.telegram.bot.kernel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.telegram.bot.kernel.database.DatabaseManager;
import org.telegram.bot.structure.Chat;
import org.telegram.bot.structure.IUser;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDatabaseManager implements DatabaseManager {

    // key = botId, value = [pts, date, seq]
    private Map<Integer, int[]> differenceData = new ConcurrentHashMap<>();

    @Override
    public @Nullable Chat getChatById(int chatId) {
        return null;
    }

    @Override
    public @Nullable IUser getUserById(int userId) {
        return null;
    }

    @Override
    public @NotNull Map<Integer, int[]> getDifferencesData() {
        return differenceData;
    }

    @Override
    public boolean updateDifferencesData(int botId, int pts, int date, int seq) {
        int[] newValue = {pts, date, seq};
        int[] previousValue = differenceData.put(botId, newValue);
        return !Arrays.equals(newValue, previousValue);
    }
}
