package ru.kpfu.itis.cll;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.postgresql.util.PSQLException;

import java.io.IOException;
import java.sql.SQLException;

public class DataBaseCreatingTest {

    @After
    public void interpretationOfResults() {
        // из-за того, что тест считается пройденным при обнаружении исключения, нужна расшифровка результатов
        // тестов
        System.out.println("ИНТЕРПРЕТАЦИЯ РЕЗУЛЬТАТОВ ТЕСТОВ\n"+
                "Если два теста не пройдены, значит, настройки базы данных верны. \n" +
                "Если два теста пройдены, значит, неверны DATA_BASE_USER или DATA_BASE_PASSWORD в классе DataBaseProperties.\n" +
                "Если не пройден dataBaseUserAndPasswordTest, а dataBasePropertiesTest пройден, " +
                "значит, неверен DATA_BASE_URL в классе DataBaseProperties\n(требуется перезапуск теста " +
                "после корректирвки DATA_BASE_URL для исключения возможности неверных DATA_BASE_USER или " +
                "DATA_BASE_PASSWORD,\nт.к. при неверном DATA_BASE_URL их проверка не происходит).");
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void dataBasePropertiesTest() throws IOException, SQLException {
        // PSQLException является подклассом SQLException, поэтому exception.expect(SQLException.class)
        // на него тоже отреагирует, следовательно, SQLException вылетает при любых неверных настройках базы
        // данных
        exception.expect(SQLException.class);
        DataBaseCreating.create();
    }

    @Test
    public void dataBaseUserAndPasswordTest() throws IOException, SQLException {
        exception.expect(PSQLException.class);
        DataBaseCreating.create();
    }
}
