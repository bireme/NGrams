/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import java.io.*;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.xml.sax.SAXException;

/**
 * Test if the duplicated document Lucene index is following the schema file.
 *
 * @author Heitor Barbieri
 * date: 20180619
 */
public class TestIndex {
    public static boolean test(final String indexDir,
                               final String schemaFile,
                               final String schemaEncoding) throws IOException,
                                                   ParserConfigurationException,
                                                   SAXException {

        final NGSchema schema = new NGSchema("", schemaFile, schemaEncoding);
        final FSDirectory directory = FSDirectory.open(new File(indexDir).toPath());  // current version
        //final FSDirectory directory = FSDirectory.open(new File(indexDir));  // Lucene 4.0
        final DirectoryReader ireader = DirectoryReader.open(directory);

        return test(ireader, schema);
    }

    public static boolean test(final IndexReader ireader,
                               final NGSchema schema) throws IOException {
        final Parameters parameters = schema.getParameters();
        final Map<String, Field> fields = parameters.getNameFields();
        boolean bad = false;

        for (int id = 0; id < ireader.maxDoc(); id++) {
            final Document doc = ireader.storedFields().document(id);

            if (id % 100000 == 0) System.out.println("+++ "+ id);
            bad = badDocument(doc, fields);
            if (bad) {
                System.out.println("BAD DOCUMENT => id: "+ doc.get("id"));
                break;
            }
        }
        ireader.close();

        return !bad;
    }

    private static boolean badDocument(final Document doc,
                                       final Map<String, Field> fields) {
        boolean ret = false;

        for (Field field : fields.values()) {
            ret = !checkRequiredField(doc, field);
            if (ret) break;
        }
        return ret;
    }

    private static boolean checkRequiredField(final Document doc,
                                              final Field field) {
        final String reqFieldName = field.requiredField;
        final String recFieldContent = doc.get(reqFieldName);

        return (reqFieldName == null) || (reqFieldName.isEmpty()) ||
             ((recFieldContent != null) && (!recFieldContent.isEmpty()));
    }
}
