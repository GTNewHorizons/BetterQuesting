package drethic.questbook.logger;

import drethic.questbook.QuestBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QBLogger {
    public static final Logger logger = LogManager.getLogger(QuestBook.MODID);
}
