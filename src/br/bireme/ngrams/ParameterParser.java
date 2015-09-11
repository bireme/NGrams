/*=========================================================================

    Copyright © 2015 BIREME/PAHO/WHO

    This file is part of NGrams.

    NGrams is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    NGrams is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with NGrams. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/

package br.bireme.ngrams;

import br.bireme.ngrams.Field.Status;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
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
 *      <sourceField pos="0"/>
 *      <idField pos="1"/>      
 *      <idxNGramField name="titulo" pos="3" minScore="0.6"/>
 *      <nGramField pos="2" name="autores" minScore="0.7" status="OPTIONAL" match="REQUIRED" requiredField="1"/> 
 *      <exactField pos="6" name="volume" status="MAX_SCORE"/>
 *      <exactField pos="9" name="numero" status="OPTIONAL" match="REQUIRED"/>
 *      <exactField pos="4" name="ano" status="REQUIRED" match="OPTIONAL" />
 *      <exactField pos="5" name="pais" status="OPTIONAL" requiredField="2" match="MAX_SCORE" values="BR,US"/>
 *      <regExpField pos="7" name="paginas" status="OPTIONAL" requiredField="2" pattern="(\d+)" groupNum="1"/>
 *      <noCompField pos="8" name="base de dados"/>
 *  </config>

 * @author Heitor Barbieri
 * date: 20150707
 */
class ParameterParser {    
    static Parameters parseParameters(final String spec) throws 
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

        final NodeList nSrcList = doc.getElementsByTagName("sourceField");
        final SourceField src = parseSourceField(nSrcList);
        
        final NodeList nIdList = doc.getElementsByTagName("idField");
        final IdField id = parseIdField(nIdList);
        
        final NodeList nIdxNGramList = doc.getElementsByTagName("idxNGramField");
        final IndexedNGramField idxNGram =  parseIdxNGramField(nIdxNGramList);        
        
        final NodeList nExactList = doc.getElementsByTagName("exactField");
        final Set<ExactField> exact =  parseExactFields(nExactList);                
        
        final NodeList nNGramList = doc.getElementsByTagName("nGramField");
        final Set<NGramField> ngram = parseNGramFields(nNGramList);                
        
        final NodeList nRegExpList = doc.getElementsByTagName("regExpField");
        final Set<RegExpField> regexp = parseRegExpFields(nRegExpList); 
        
        final NodeList nNoCompareList = doc.getElementsByTagName("noCompField");
        final Set<NoCompareField> nocomp = parseNoCompareFields(nNoCompareList);
        
        final NodeList nScoreList = doc.getElementsByTagName("score");
        final TreeSet<Score> scrs = parseScores(nScoreList);
                        
        return new Parameters(scrs, src, id, idxNGram, exact, ngram, regexp, 
                                                                        nocomp);
    }
       
    static SourceField parseSourceField(final NodeList nSrcList) 
                                                            throws IOException {
        assert nSrcList != null;
        
        if (nSrcList.getLength() != 1) {
            throw new IOException("number of 'srcField' is not one");
        }
        
        final Node nNode = nSrcList.item(0);
        if (nNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IOException("'srcfield' is not an Element node");
        }
        final Element eElement = (Element) nNode;
        final String pos = eElement.getAttribute("pos").trim();
        if (pos.isEmpty()) {
            throw new IOException("missing 'pos' attribute");
        }
        final SourceField src = new SourceField(Integer.parseInt(pos));
        return src;
    }
    
    static IdField parseIdField(final NodeList nIdList) throws IOException {
        assert nIdList != null;
        
        if (nIdList.getLength() != 1) {
            throw new IOException("number of 'idField' is not one");
        }
        
        final Node nNode = nIdList.item(0);
        if (nNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IOException("'idfield' is not an Element node");
        }
        final Element eElement = (Element) nNode;
        final String pos = eElement.getAttribute("pos").trim();
        if (pos.isEmpty()) {
            throw new IOException("missing 'pos' attribute");
        }
        final IdField id = new IdField(Integer.parseInt(pos));
        return id;
    }
    
    static IndexedNGramField parseIdxNGramField(final NodeList nIdxNGramList) 
                                                            throws IOException {
        assert nIdxNGramList != null;
        
        if (nIdxNGramList.getLength() != 1) {
            throw new IOException("number of 'idxNGramField' is not one");
        }
        final Node nNode = nIdxNGramList.item(0);

        if (nNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IOException("'IdxNGramField' is not an Element node");
        }
        final Element eElement = (Element) nNode;
        final String name = eElement.getAttribute("name").trim();
        if (name.isEmpty()) {
            throw new IOException("missing 'name' attribute");
        }
        final String pos = eElement.getAttribute("pos").trim();
        if (pos.isEmpty()) {
            throw new IOException("missing 'pos' attribute");
        }
        final String minScore = eElement.getAttribute("minScore").trim();
        if (minScore.isEmpty()) {
            throw new IOException("missing 'minScore' attribute");
        }
        final IndexedNGramField idxNGram = new IndexedNGramField(
              name,
              Integer.parseInt(pos),
              Float.parseFloat(minScore));
        return idxNGram;
    }
    
    static Set<NGramField> parseNGramFields(final NodeList nNGramList) 
                                                            throws IOException {
        assert nNGramList != null;
        
        final Set<NGramField> ngramSet = new HashSet<>();
        for (int idx = 0; idx < nNGramList.getLength(); idx++) { 
            final Node nNode = nNGramList.item(idx);
            
            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("'NGramField' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final String name = eElement.getAttribute("name").trim();
            if (name.isEmpty()) {
                throw new IOException("missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("missing 'pos' attribute");
            }
            final Status status;
            final String statusStr = eElement.getAttribute("status").trim();
            if (statusStr.isEmpty()) {
                status = Status.OPTIONAL;
            } else {
                if ((!statusStr.equals("OPTIONAL")) &&
                    (!statusStr.equals("REQUIRED")) &&
                    (!statusStr.equals("MAX_SCORE"))) {
                    throw new IOException("invalid 'status' attribute value: "
                                                                   + statusStr);
                }
                status = Status.valueOf(statusStr);
            }          
            final Status match;
            final Set<String> content;
            final String matchStr = eElement.getAttribute("match").trim();
            if (matchStr.isEmpty()) {
                match = null;
                content = null;
            } else {
                if ((!matchStr.equals("OPTIONAL")) &&
                    (!matchStr.equals("REQUIRED")) &&
                    (!matchStr.equals("MAX_SCORE"))) {
                    throw new IOException("invalid 'match' attribute value: "
                                                                    + matchStr);
                }
                match = Status.valueOf(matchStr);
                if (match.equals(Status.MAX_SCORE)) {
                    final String contentStr = eElement.getAttribute("values")
                                                                        .trim();
                    if (contentStr == null) {
                        throw new IOException("missing 'value' attribute");
                    } 
                    final String[] split = contentStr.split(" *, *");
                    content = new HashSet<>(Arrays.asList(split));
                } else {
                    content = null;
                }
            }
            final String requiredField = eElement.getAttribute("requiredField")
                                                                        .trim();            
            final String minScore = eElement.getAttribute("minScore").trim();
            if (minScore.isEmpty()) {
                throw new IOException("missing 'minScore' attribute");
            }
            
            final NGramField nGram = new NGramField(
                  name,
                  Integer.parseInt(pos),
                  status,
                  content,
                  match,
                  (requiredField.isEmpty()) ? -1 
                                            : Integer.parseInt(requiredField),
                  Float.parseFloat(minScore));
            ngramSet.add(nGram);
        }
        return ngramSet;
    }
    
    static Set<RegExpField> parseRegExpFields(final NodeList nRegExpList) 
                                                            throws IOException {
        assert nRegExpList != null;
        
        final Set<RegExpField> regExpSet = new HashSet<>();
        for (int idx = 0; idx < nRegExpList.getLength(); idx++) { 
            final Node nNode = nRegExpList.item(idx);
            
            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("'NGramField' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final String name = eElement.getAttribute("name").trim();
            if (name.isEmpty()) {
                throw new IOException("missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("missing 'pos' attribute");
            }
            final Status status;
            final String statusStr = eElement.getAttribute("status").trim();
            if (statusStr.isEmpty()) {
                status = Status.OPTIONAL;
            } else {
                if ((!statusStr.equals("OPTIONAL")) &&
                    (!statusStr.equals("REQUIRED")) &&
                    (!statusStr.equals("MAX_SCORE"))) {
                    throw new IOException("invalid 'status' attribute value: "
                                                                   + statusStr);
                }
                status = Status.valueOf(statusStr);
            }
            final Status match;
            final Set<String> content;
            final String matchStr = eElement.getAttribute("match").trim();
            if (matchStr.isEmpty()) {
                match = null;
                content = null;
            } else {
                if ((!matchStr.equals("OPTIONAL")) &&
                    (!matchStr.equals("REQUIRED")) &&
                    (!matchStr.equals("MAX_SCORE"))) {
                    throw new IOException("invalid 'match' attribute value: "
                                                                    + matchStr);
                }
                match = Status.valueOf(matchStr);
                if (match.equals(Status.MAX_SCORE)) {
                    final String contentStr = eElement.getAttribute("values")
                                                                        .trim();
                    if (contentStr.isEmpty()) {
                        throw new IOException("missing 'value' attribute");
                    } 
                    final String[] split = contentStr.split(" *, *");
                    content = new HashSet<>(Arrays.asList(split));
                } else {
                    content = null;
                }
            }
            final String requiredField = eElement.getAttribute("requiredField")
                                                                        .trim();            
            final String pattern = eElement.getAttribute("pattern").trim();
            if (pattern.isEmpty()) {
                throw new IOException("missing 'pattern' attribute");
            }
            final String sgroupNum = eElement.getAttribute("groupNum").trim();
            if (sgroupNum.isEmpty()) {
                throw new IOException("missing 'groupNum' attribute");
            }            
            final RegExpField regexpf = new RegExpField(
                  name,
                  Integer.parseInt(pos),
                  status,
                  content,
                  match,
                  (requiredField.trim().isEmpty())
                                             ? -1 
                                             : Integer.parseInt(requiredField),
                  pattern,
                  Integer.parseInt(sgroupNum));
            regExpSet.add(regexpf);
        }
        return regExpSet;
    }
    
    static Set<ExactField> parseExactFields(final NodeList nExactList) 
                                                            throws IOException {
        assert nExactList != null;
        
        final Set<ExactField> exactSet = new HashSet<>();
        for (int idx = 0; idx < nExactList.getLength(); idx++) { 
            final Node nNode = nExactList.item(idx);
            
            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("'field' is not an Element node");
            }
            final Element eElement = (Element) nNode;            
            final String name = eElement.getAttribute("name").trim();
            if (name.isEmpty()) {
                throw new IOException("missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("missing 'pos' attribute");
            }
            final Status status;
            final String statusStr = eElement.getAttribute("status").trim();
            if (statusStr.isEmpty()) {
                status = Status.OPTIONAL;
            } else {
                if ((!statusStr.equals("OPTIONAL")) &&
                    (!statusStr.equals("REQUIRED")) &&
                    (!statusStr.equals("MAX_SCORE"))) {
                    throw new IOException("invalid 'status' attribute value: "
                                                                   + statusStr);
                }
                status = Status.valueOf(statusStr);
            }
            final Status match;
            final Set<String> content;
            final String matchStr = eElement.getAttribute("match").trim();
            if (matchStr.isEmpty()) {
                match = null;
                content = null;
            } else {
                if ((!matchStr.equals("OPTIONAL")) &&
                    (!matchStr.equals("REQUIRED")) &&
                    (!matchStr.equals("MAX_SCORE"))) {
                    throw new IOException("invalid 'match' attribute value: "
                                                                    + matchStr);
                }
                match = Status.valueOf(matchStr);
                if (match.equals(Status.MAX_SCORE)) {
                    final String contentStr = eElement.getAttribute("values")
                                                                        .trim();
                    if (contentStr.isEmpty()) {
                        throw new IOException("missing 'value' attribute");
                    } 
                    final String[] split = contentStr.split(" *, *");
                    content = new HashSet<>(Arrays.asList(split));
                } else {
                    content = null;
                }
            }
            final String requiredField = eElement.getAttribute("requiredField")
                                                                        .trim();
            final ExactField def = new ExactField(
                  name,
                  Integer.parseInt(pos),
                  status,
                  content,
                  match,
                  (requiredField.trim().isEmpty())
                                             ? -1 
                                             : Integer.parseInt(requiredField));
            exactSet.add(def);
        }
        return exactSet;
    }
    
    static Set<NoCompareField> parseNoCompareFields(final NodeList nCompList) 
                                                            throws IOException {
        assert nCompList != null;
        
        final Set<NoCompareField> noCompareSet = new HashSet<>();
        for (int idx = 0; idx < nCompList.getLength(); idx++) { 
            final Node nNode = nCompList.item(idx);
            
            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("'field' is not an Element node");
            }
            final Element eElement = (Element) nNode;            
            final String name = eElement.getAttribute("name").trim();
            if (name.isEmpty()) {
                throw new IOException("missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos").trim();
            if (pos.isEmpty()) {
                throw new IOException("missing 'pos' attribute");
            }
            final NoCompareField def = new NoCompareField(name, 
                                                         Integer.parseInt(pos));
            noCompareSet.add(def);
        }
        return noCompareSet;
    }
    
    static TreeSet<Score> parseScores(final NodeList nScoreList) 
                                                            throws IOException {
        assert nScoreList != null;
            
        final int len = nScoreList.getLength();
        if (len == 0) {
            throw new IOException("empty score set");
        }        
        final TreeSet<Score> scoreSet = new TreeSet<>();
        for (int idx = 0; idx < len; idx++) { 
            final Node nNode = nScoreList.item(idx);
            
            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("'score' is not an Element node");
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
