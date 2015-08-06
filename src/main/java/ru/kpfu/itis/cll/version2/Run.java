package ru.kpfu.itis.cll.version2;

import ru.kpfu.itis.cll.DataBaseCreating;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Главный класс приложения, отсюда происходит запуск - построение словаря тональности на основе РуТез.
 *
 * Версия 2 рассматривает слова как текстовые входы. Записывает исходное слово в таблицу words, находит
 * соответствующие этому слову текстовые входы, записывает их в таблицу text_entries. Сопоставляет
 * текстовые входы из базы данных концептам, вычисляет для каждого концепта его sentiment_id по количеству
 * связанных с ним текстовых входов с позитивным/негативным sentiment.
 */
public class Run {
    /**
     * Создаёт таблицы в базе данных, заполняет таблицу sentiments, подготавливает в базе данных информацию
     * об уже имеющихся словах из словарей, вычисляет тональность концептов по связанным с ними текстовым
     * входам, выводит полученный словарь с тональностью и соответствиями концепт-текстовые входы (всё это
     * происходит посредством последовательного запуска из этого метода других соответствующих методов).
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
        // находит связанные с текстовыми входами концепты и вычисляет их тональность
        FindingSentimentsForConcepts.mappingAndDerivingSentimentForConcepts();
        // вывод всех полученных концептов с тональностью и соответствующими концепту текстовыми входами
        OutputProcessing.sentimentDictionaryOutput();
    }
}
