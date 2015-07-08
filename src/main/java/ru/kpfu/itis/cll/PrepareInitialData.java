package ru.kpfu.itis.cll;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Подготовка в базе данных информации об уже имеющихся словах из словарей.
 * Рассматривает исходные слова как концепты, а не текстовые входы.
 */
public class PrepareInitialData {

    // не позволяет создавать экземпляры данного класс, класс будет использоваться только как набор
    // static-методов дл¤ решения определённой задачи
    private PrepareInitialData() {}

    /**
     * Кладёт исходные словари лексики в базу данных в таблицу words, заранее созданные словари находятся
     * в resources/inputDictionaries.
     * Находит для каждого слова его concept_id в файле resources/ruThesData/concepts.xml.
     */
    public static void insertInitialDataInDB(Map<String, Integer> dictionariesWithSentimentId) throws FileNotFoundException, SQLException, ClassNotFoundException {
        // обрабатываем все поданные на вход методу словари
        for (Map.Entry<String, Integer> dictionariesWithSentimentIdEntry : dictionariesWithSentimentId.entrySet()) {
            Scanner dictionary = new Scanner(new File(dictionariesWithSentimentIdEntry.getKey()));
            Scanner concepts = new Scanner(new File("resources/ruThesData/concepts.xml"));
            Pattern pId = Pattern.compile(" *<concept id=\".*?\">");
            Pattern pAnyName = Pattern.compile(" *<name>.*?</name>");
            String idSuspect = "", nameSuspect;

            // берём словарь
            while (dictionary.hasNextLine()) {
                String name = dictionary.nextLine();
                Integer id = null;
                boolean idFound = false;
                // оба файла (и словарь, и concepts.xml) берём отсортированными, тогда возможно их "наложение"
                // друг на друга, уменьшается сложность алгоритма
                // мы продолжаем идти по файлу concepts, а не идём каждый раз с самого начала,
                // если словарь будет не отсортирован, алгоритм отработает неверно.
                // пока не дошли до конца файла или не нашли conceptId или не убедились, что он не существует
                while(concepts.hasNextLine() && !idFound) {
                    // нужно проверить на наличие двух строк, чтобы проверить id и name, которые расположение
                    // в двух строках друг за другом
                    if (concepts.hasNextLine()) {
                        // в файле сначала пишется id, а на следующей строке name
                        // если после предыдущей итерации у нас не оставался необработанный id,
                        // то обрабатываем следующий, иначе в строке idSuspect остаётся необработанный id
                        if (idSuspect.equals("")) {
                            idSuspect = concepts.nextLine();
                        }
                        nameSuspect = concepts.nextLine();

                        if (pAnyName.matcher(nameSuspect).matches()) {
                            // нужно для проверки выхода за лексикографические границы наложения двух файлов
                            // в concepts.xml слова иногда имеют пояснения в скобках после самого слова,
                            // а также могут идти через запятую
                            // например: БОРОЗДИТЬ; БОРОЗДИТЬ (ПЕРЕСЕКАТЬ В РАЗНЫХ НАПРАВЛЕНИЯХ);
                            // БОРОЗДИТЬ, ПРОПАХАТЬ БОРОЗДЫ
                            String stringWithName = nameSuspect.split(">")[1].split("<")[0].split(" \\(")[0].split(", ")[0];
                            // если мы лексикографически вышли за границы concepts в поисках id для name
                            while (stringWithName.compareTo(name.toUpperCase()) > 0 && dictionary.hasNextLine()) {
                                insertIntoWords(null, name, dictionariesWithSentimentIdEntry.getValue());
                                // переходим на следующее слово в словаре
                                name = dictionary.nextLine();
                            }

                            // в concepts.txt слова написаны большими буквами, а в исходных словарях маленькими
                            String nameUpperCase = name.toUpperCase();
                            // если нашли данное слово в файле с концептами
                            if (stringWithName.equals(nameUpperCase)) {
                                // в предыдущей строке перед name всегда пишется его id
                                String [] stringsWithId = idSuspect.split("\"");
                                id = Integer.parseInt(stringsWithId[1]);
                                idFound = true;
                                idSuspect = "";
                            }
                            // если id пока не был найден и nameSuspect не содержит следующий id,
                            // то обработка начнётся с новых строк
                            else {
                                idSuspect = "";
                            }
                        }
                        // вызвали дважды nextLine для scanner, он назад не вернётся,
                        // придётся проверить nameSuspect на id
                        // можно было бы использовать LineNumberReader, но это не сильно проще
                        else if (pId.matcher(nameSuspect).matches()){
                            idSuspect = nameSuspect;
                        }
                        // если id пока не был найден и nameSuspect не содержит следующий id,
                        // то обработка начнётся с новых строк
                        else {
                            idSuspect = "";
                        }
                    }
                }

                insertIntoWords(id, name, dictionariesWithSentimentIdEntry.getValue());
            }
        }
        System.out.println("After method insertInitialDataInDB");
    }

    /**
     * Записывает в базу данных в таблицу words слово из словаря с найденным concept_id из файла concepts.xml,
     * с установленным заранее sentiment_id и с пометкой from_input = true.
     */
    public static void insertIntoWords(Integer id, String name, Integer sentimentId) throws ClassNotFoundException, SQLException {
        Class.forName("org.postgresql.Driver");
        Connection cn = DriverManager.getConnection(DataBaseProperties.DATA_BASE_URL, DataBaseProperties.DATA_BASE_USER, DataBaseProperties.DATA_BASE_PASSWORD);
        Statement st = cn.createStatement();

        // если не нашли id, поле concept_id заполнится как null
        ResultSet rs = st.executeQuery("insert into words (concept_id, name, sentiment_id, from_input) values " +
                "(" + id + ", '" + name + "', " + sentimentId + ", true) " +
                "returning id, concept_id, \"name\", sentiment_id;");
        rs.next();
        System.out.println("id = " + rs.getInt("id") + ", concept_id = " + rs.getInt("concept_id") +
                ", name = " + rs.getString("name") + ", sentiment_id = " + rs.getString("sentiment_id"));

        cn.close();
        st.close();
    }
}
