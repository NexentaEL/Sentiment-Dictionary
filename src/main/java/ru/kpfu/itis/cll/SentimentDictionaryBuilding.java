package ru.kpfu.itis.cll;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Главный класс приложения, отсюда происходит запуск.
 */
public class SentimentDictionaryBuilding {
    public static void main(String[] args) throws SQLException, IOException, ClassNotFoundException {
        // создание коллекции исходных словарей с указанием их sentiment_id
        // 1 - positive; 2 - negative; 3 - positive/negative; 4 - neutral
        Map<String, Integer> dictionariesWithSentimentId = new HashMap<>();
        dictionariesWithSentimentId.put("resources/inputDictionaries/adjs-pos.txt", 1);
        dictionariesWithSentimentId.put("resources/inputDictionaries/adjs-neg.txt", 2);

        // создание всех таблиц и заполнение таблицы sentiments в базе данных
        DataBaseCreating.create();
        // подготовка в базе данных информации об уже имеющихся словах из словарей
        PrepareInitialData.insertInitialDataInDB(dictionariesWithSentimentId);
        // обработка пар понятие-понятие, когда второе понятие ниже первого по иерархии
        HierarchyProcessing.verticalRelationsBuilding();
        // вывод полученного словаря с тональностью и иерархией
        OutputProcessing.sentimentDictionaryOutput();
    }
}
