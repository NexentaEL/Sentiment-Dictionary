package ru.kpfu.itis.cll;

/**
 * Настройки БД, используемые остальными классами приложения.
 */
public final class DataBaseProperties {
    public static final String DATA_BASE_URL = "jdbc:postgresql://localhost:5432/sentiment_dictionary";
    public static final String DATA_BASE_USER = "postgres";
    public static final String DATA_BASE_PASSWORD= "postgres";

    private DataBaseProperties() {}
}
