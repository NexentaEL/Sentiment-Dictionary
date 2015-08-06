package ru.kpfu.itis.cll.version1;

import ru.kpfu.itis.cll.DataBaseCreating;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Главный класс приложения, отсюда происходит запуск - построение словаря тональности на основе РуТез.
 *
 * Версия 1 рассматривает исходные слова как концепты. Записывает исходное слово в таблицу words,
 * находит соответствующие этому слову концепты, записывает их в таблицу concepts. После происходит
 * обработка концептов, которые по иерархии стоят ниже тех, которые уже имеются в БД. Новые концепты,
 * стоящие ниже, записываются в БД с sentiment_id вышестоящего концепта.
 */
public class Run {
    /**
     * Создаёт таблицы в базе данных, заполняет таблицу sentiments, подготавливает в базе данных информацию
     * об уже имеющихся словах из словарей, обрабатывает пары концепт-концепт, когда второй концепт ниже
     * первого по иерархии, выводит полученный словарь с тональностью и иерархией (всё это происходит посредством
     * последовательного запуска из этого метода других соответствующих методов).
     * @param args аргументы командной строки (не используются)
     * @throws SQLException
     * @throws IOException
     */
    public static void main(String[] args) throws SQLException, IOException {
        // создание коллекции исходных словарей с указанием их sentiment_id
        // 1 - positive; 2 - negative; 3 - positive/negative
        Map<String, Integer> dictionariesWithSentimentId = new HashMap<>();
        dictionariesWithSentimentId.put("resources/inputDictionaries/adjs-pos.txt", 1);
        dictionariesWithSentimentId.put("resources/inputDictionaries/adjs-neg.txt", 2);

        // создание всех таблиц и заполнение таблицы sentiments в базе данных
        DataBaseCreating.create();
        // подготовка в базе данных информации об уже имеющихся словах из словарей
        PrepareInitialData.insertInitialDataInDB(dictionariesWithSentimentId);
        // обработка пар концепт-концепт, когда второй концепт ниже первого по иерархии
        HierarchyProcessing.verticalRelationsBuilding();
        // вывод всех полученных концептов с тональностью и иерархией
        OutputProcessing.sentimentDictionaryOutput();
    }
}
