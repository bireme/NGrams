/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.ngrams;

import br.bireme.ngrams.Field.Status;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *  <config>
 *      <score minValue="1" minFields="1"/>
 *      <score minValue="0.9" minFields="1"/>
 *      <score minValue="0.7" minFields="3"/>
 *      <score minValue="0.6" minFields="4"/>
 *      <databaseField pos="0"/>
 *      <idField pos="2"/>
 *      <authorsField pos="3" name="autores"
 *      <idxNGramField pos="4" name="titulo"/>
 *      <nGramField pos="5" name="outrosAutores" minScore="0.7" match="REQUIRED" requiredField="titulo"/>
 *      <exactField pos="6" name="volume" />
 *      <exactField pos="8" name="numero" match="REQUIRED"/>
 *      <exactField pos="12" name="ano" />
 *      <exactField pos="13" name="pais" requiredField="numero" match="MAX_SCORE" values="BR,US"/>
 *      <regExpField pos="15" name="paginas" requiredField="numero" pattern="(\d+)" groupNum="1"/>
 *      <noCompField pos="20" name="base de dados"/>
 *      <diceField pos="21" name="resumo" minScore="0.81" match="REQUIRED"/>
 *  </config>

 * @author Heitor Barbieri
 * date: 20150707
 */
class ParameterParser {
    static Parameters parseParameters(final String name,
                                      final String spec) throws
                                                   IOException,
                                                   ParserConfigurationException,
                                                   SAXException {
        if (spec == null) {
            throw new NullPointerException("spec");
        }
        final DocumentBuilderFactory dbFactory =
                                           DocumentBuilderFactory.newInstance();
        final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        final Document doc = dBuilder.parse(new InputSource(
                                                       new StringReader(spec)));
        doc.getDocumentElement().normalize();

        final NodeList nDbList = doc.getElementsByTagName("databaseField");
        final DatabaseField src = parseDatabaseField(name, nDbList);

        final NodeList nIdList = doc.getElementsByTagName("idField");
        final IdField id = parseIdField(name, nIdList);

        final NodeList nAuthorsList = doc.getElementsByTagName("authorsField");
        final AuthorsField authors = parseAuthorsField(name, nAuthorsList);

        final NodeList nIdxNGramList = doc.getElementsByTagName("idxNGramField");
        final IndexedNGramField idxNGram =  parseIdxNGramField(name, nIdxNGramList);

        final NodeList nExactList = doc.getElementsByTagName("exactField");
        final Set<ExactField> exact =  parseExactFields(name, nExactList);
 
        final NodeList nNGramList = doc.getElementsByTagName("nGramField");
        final Set<NGramField> ngram = parseNGramFields(name, nNGramList);

        final NodeList nRegExpList = doc.getElementsByTagName("regExpField");
        final Set<RegExpField> regexp = parseRegExpFields(name, nRegExpList);

        final NodeList nNoCompareList = doc.getElementsByTagName("noCompField");
        final Set<NoCompareField> nocomp = parseNoCompareFields(name, nNoCompareList);

        final NodeList nDiceList = doc.getElementsByTagName("diceField");
        final Set<DiceField> dice = parseDiceFields(name, nDiceList);

        final NodeList nScoreList = doc.getElementsByTagName("score");
        final TreeSet<Score> scrs = parseScores(name, nScoreList);

        return new Parameters(scrs, src, id, authors, idxNGram, exact, ngram,
                                                          regexp, nocomp, dice);
    }

    static DatabaseField parseDatabaseField(final String name,
                                            final NodeList nDbList)
                                                            throws IOException {
        assert nDbList != null;

        if (nDbList.getLength() != 1) {
            throw new IOException("[" + name +
                                    "] - number of 'databaseField' is not one");
        }

        final Node nNode = nDbList.item(0);
        if (nNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IOException("[" + name +
                                  "] - 'databaseField' is not an Element node");
        }
        final Element eElement = (Element) nNode;
        final String pos = eElement.getAttribute("pos").trim();
        if (pos.isEmpty()) {
            throw new IOException("[" + name +
                                 "] - databaseField - missing 'pos' attribute");
        }
        final DatabaseField src = new DatabaseField(Integer.parseInt(pos));
        return src;
    }

    static IdField parseIdField(final String name,
                                final NodeList nIdList) throws IOException {
        assert nIdList != null;

        if (nIdList.getLength() != 1) {
            throw new IOException("[" + name +
                                          "] - number of 'idField' is not one");
        }

        final Node nNode = nIdList.item(0);
        if (nNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IOException("[" + name +
                                        "] - 'idField' is not an Element node");
        }
        final Element eElement = (Element) nNode;
        final String pos = eElement.getAttribute("pos").trim();
        if (pos.isEmpty()) {
            throw new IOException("[" + name +
                                       "] - idField - missing 'pos' attribute");
        }
        final IdField id = new IdField(Integer.parseInt(pos));
        return id;
    }

    static AuthorsField parseAuthorsField(final String name,
                                          final NodeList nIdList) throws IOException {
        assert nIdList != null;

        if (nIdList.getLength() == 0) {
            return null;
        }

        final Node nNode = nIdList.item(0);
        if (nNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IOException("[" + name +
                                   "] - 'authorsField' is not an Element node");
        }
        final Element eElement = (Element) nNode;
        final String name1 = eElement.getAttribute("name").trim();
        if (name1.isEmpty()) {
            throw new IOException("[" + name +
                                "] - authorsField - missing 'name' attribute");
        }
        final String pos = eElement.getAttribute("pos").trim();
        if (pos.isEmpty()) {
            throw new IOException("[" + name +
                                  "] - authorsField - missing 'pos' attribute");
        }
        final Status match;
        String matchStr = eElement.getAttribute("match").trim();
        if (matchStr.isEmpty()) {
            matchStr = "REQUIRED";
        }
        if ((!matchStr.equals("REQUIRED")) &&
            (!matchStr.equals("MAX_SCORE")) &&
             !matchStr.equals("DENY_DUP")) {
            throw new IOException("[" + name +
                        "] - AuthorsField - invalid 'match' attribute value: "
                                                            + matchStr);
        }
        match = Status.valueOf(matchStr);
        final AuthorsField authors =
                          new AuthorsField(name1, Integer.parseInt(pos), match);
        return authors;
    }

    static IndexedNGramField parseIdxNGramField(final String name,
                                                final NodeList nIdxNGramList)
                                                            throws IOException {
        assert nIdxNGramList != null;

        if (nIdxNGramList.getLength() != 1) {
            throw new IOException("[" + name +
                                    "] - number of 'idxNGramField' is not one");
        }
        final Node nNode = nIdxNGramList.item(0);

        if (nNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IOException("[" + name +
                                  "] - 'idxNGramField' is not an Element node");
        }
        final Element eElement = (Element) nNode;
        final String name1 = eElement.getAttribute("name").trim();
        if (name1.isEmpty()) {
            throw new IOException("[" + name +
                                "] - idxNGramField - missing 'name' attribute");
        }
        final String pos = eElement.getAttribute("pos").trim();
        if (pos.isEmpty()) {
            throw new IOException("[" + name +
                                 "] - idxNGramField - missing 'pos' attribute");
        }
        final IndexedNGramField idxNGram = new IndexedNGramField(
              name1,
              Integer.parseInt(pos));
        return idxNGram;
    }

    static Set<NGramField> parseNGramFields(final String name,
                                            final NodeList nNGramList)
                                                            throws IOException {
        assert nNGramList != null;

        final Set<NGramField> ngramSet = new HashSet<>();
        for (int idx = 0; idx < nNGramList.getLength(); idx++) {
            final Node nNode = nNGramList.item(idx);

            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("[" + name +
                                     "] - 'nGramField' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final String name1 = eElement.getAttribute("name").trim();
            if (name1.isEmpty()) {
                throw new IOException("[" + name + ""
                        + "         ] - nGramField - missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("[" + name +
                                    "] - nGramField - missing 'pos' attribute");
            }
            final Status match;
            String matchStr = eElement.getAttribute("match").trim();
            if (matchStr.isEmpty()) {
                matchStr = "REQUIRED";
            }
            if ((!matchStr.equals("REQUIRED")) &&
                (!matchStr.equals("MAX_SCORE")) &&
                (!matchStr.equals("DENY_DUP"))) {
                throw new IOException("[" + name +
                            "] - nGramField - invalid 'match' attribute value: "
                                                                + matchStr);
            }
            match = Status.valueOf(matchStr);
            final String requiredField = eElement.getAttribute("requiredField")
                                                                        .trim();
            final String minScore = eElement.getAttribute("minScore").trim();
            if (minScore.isEmpty()) {
                throw new IOException("[" + name +
                               "] - nGramField - missing 'minScore' attribute");
            }

            final NGramField nGram = new NGramField(
                  name1,
                  Integer.parseInt(pos),
                  match,
                  (requiredField.isEmpty() ? null : requiredField),
                  Float.parseFloat(minScore));
            ngramSet.add(nGram);
        }
        return ngramSet;
    }

    static Set<RegExpField> parseRegExpFields(final String name,
                                              final NodeList nRegExpList)
                                                            throws IOException {
        assert nRegExpList != null;

        final Set<RegExpField> regExpSet = new HashSet<>();
        for (int idx = 0; idx < nRegExpList.getLength(); idx++) {
            final Node nNode = nRegExpList.item(idx);

            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("[" + name +
                                    "] - 'regExpField' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final String name1 = eElement.getAttribute("name").trim();
            if (name1.isEmpty()) {
                throw new IOException("[" + name +
                                  "] - regExpField - missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("[" + name +
                                   "] - regExpField - missing 'pos' attribute");
            }
            final Status match;
            String matchStr = eElement.getAttribute("match").trim();
            if (matchStr.isEmpty()) {
                matchStr = "REQUIRED";
            }
            if ((!matchStr.equals("REQUIRED")) &&
                (!matchStr.equals("MAX_SCORE")) &&
                (!matchStr.equals("DENY_DUP"))) {
                throw new IOException("[" + name +
                        "] - regExpField - invalid 'match' attribute value: "
                                                                + matchStr);
            }
            match = Status.valueOf(matchStr);
            final String requiredField = eElement.getAttribute("requiredField")
                                                                        .trim();
            final String pattern = eElement.getAttribute("pattern").trim();
            if (pattern.isEmpty()) {
                throw new IOException("[" + name +
                               "] - regExpField - missing 'pattern' attribute");
            }
            final String sgroupNum = eElement.getAttribute("groupNum").trim();
            if (sgroupNum.isEmpty()) {
                throw new IOException("[" + name +
                              "] - regExpField - missing 'groupNum' attribute");
            }
            final RegExpField regexpf = new RegExpField(
                  name1,
                  Integer.parseInt(pos),
                  match,
                  (requiredField.trim().isEmpty()) ? null : requiredField,
                  pattern,
                  Integer.parseInt(sgroupNum));
            regExpSet.add(regexpf);
        }
        return regExpSet;
    }

    static Set<ExactField> parseExactFields(final String name,
                                            final NodeList nExactList)
                                                            throws IOException {
        assert nExactList != null;

        final Set<ExactField> exactSet = new HashSet<>();
        if (nExactList != null) {
            int len = nExactList.getLength();
            int y = len;
        }
        
        for (int idx = 0; idx < nExactList.getLength(); idx++) {
            final Node nNode = nExactList.item(idx);

            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("[" + name +
                                     "] - 'exactField' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final String name1 = eElement.getAttribute("name").trim();
            if (name1.isEmpty()) {
                throw new IOException("[" + name +
                                   "] - exactField - missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("[" + name +
                                    "] - exactField - missing 'pos' attribute");
            }
            final Status match;
            String matchStr = eElement.getAttribute("match").trim();
            if (matchStr.isEmpty()) {
                matchStr = "REQUIRED";
            }
            if ((!matchStr.equals("REQUIRED")) &&
                (!matchStr.equals("MAX_SCORE")) &&
                (!matchStr.equals("DENY_DUP"))) {
                throw new IOException("[" + name +
                            "] - exactField - invalid 'match' attribute value: "
                                                                + matchStr);
            }
            match = Status.valueOf(matchStr);
            final String requiredField = eElement.getAttribute("requiredField")
                                                                        .trim();
            final ExactField def = new ExactField(
                  name1,
                  Integer.parseInt(pos),
                  match,
                  (requiredField.trim().isEmpty()) ? null : requiredField);
            exactSet.add(def);
        }
        return exactSet;
    }

    static Set<NoCompareField> parseNoCompareFields(final String name,
                                                    final NodeList nCompList)
                                                            throws IOException {
        assert nCompList != null;

        final Set<NoCompareField> noCompareSet = new HashSet<>();
        for (int idx = 0; idx < nCompList.getLength(); idx++) {
            final Node nNode = nCompList.item(idx);

            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("[" + name +
                                    "] - 'noCompField' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final String name1 = eElement.getAttribute("name").trim();
            if (name1.isEmpty()) {
                throw new IOException("[" + name +
                                  "] - noCompField - missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("[" + name +
                                   "] - noCompField - missing 'pos' attribute");
            }
            final NoCompareField def = new NoCompareField(name1,
                                                        Integer.parseInt(pos));
            noCompareSet.add(def);
        }
        return noCompareSet;
    }

    static Set<DiceField> parseDiceFields(final String name,
                                          final NodeList nDiceList)
                                                            throws IOException {
        assert nDiceList != null;

        final Set<DiceField> diceSet = new HashSet<>();
        for (int idx = 0; idx < nDiceList.getLength(); idx++) {
            final Node nNode = nDiceList.item(idx);

            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("[" + name +
                                     "] - 'nDiceField' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final String name1 = eElement.getAttribute("name").trim();
            if (name1.isEmpty()) {
                throw new IOException("[" + name + ""
                        + "         ] - nDiceField - missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("[" + name +
                                    "] - nDiceField - missing 'pos' attribute");
            }
            final Status match;
            String matchStr = eElement.getAttribute("match").trim();
            if (matchStr.isEmpty()) {
                matchStr = "REQUIRED";
            }
            if ((!matchStr.equals("REQUIRED")) &&
                (!matchStr.equals("MAX_SCORE")) &&
                (!matchStr.equals("DENY_DUP"))) {
                throw new IOException("[" + name +
                            "] - nDiceField - invalid 'match' attribute value: "
                                                                + matchStr);
            }
            match = Status.valueOf(matchStr);
            final String requiredField = eElement.getAttribute("requiredField")
                                                                        .trim();
            final String minScore = eElement.getAttribute("minScore").trim();
            if (minScore.isEmpty()) {
                throw new IOException("[" + name +
                               "] - nDiceField - missing 'minScore' attribute");
            }

            final DiceField dice = new DiceField(
                  name1,
                  Integer.parseInt(pos),
                  match,
                  (requiredField.isEmpty() ? null : requiredField),
                  Float.parseFloat(minScore));
            diceSet.add(dice);
        }
        return diceSet;
    }

    static TreeSet<Score> parseScores(final String name,
                                      final NodeList nScoreList)
                                                            throws IOException {
        assert nScoreList != null;

        final int len = nScoreList.getLength();
        if (len == 0) {
            throw new IOException("[" + name + "] - empty score set");
        }
        final TreeSet<Score> scoreSet = new TreeSet<>();
        for (int idx = 0; idx < len; idx++) {
            final Node nNode = nScoreList.item(idx);

            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("[" + name +
                                          "] - 'score' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final Score score = new Score(
                    Float.parseFloat(eElement.getAttribute("minValue").trim()),
                    Integer.parseInt(eElement.getAttribute("minFields").trim()));
            scoreSet.add(score);
        }

        return scoreSet;
    }
}
