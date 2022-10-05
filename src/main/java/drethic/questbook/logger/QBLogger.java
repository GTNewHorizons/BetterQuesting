package drethic.questbook.logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import drethic.questbook.QuestBook;

public class QBLogger {
    public static final Logger logger = LogManager.getLogger(QuestBook.MODID);
}
