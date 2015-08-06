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
 *      <idField pos="0"/>
 *      <idxNGramField name="titulo" pos="3" minScore="0.6" maxScoreIfMissingFlds="6,1" />
 *      <nGramField pos="2" name="autores" minScore="0.7" status="optional" match="required" requiredField="1"/> 
 *      <exactField pos="6" name="volume" status="required"/>
 *      <exactField pos="1" name="numero" status="optional" match="required"/>
 *      <exactField pos="4" name="ano" status="required" match="optional"/>
 *      <exactField pos="5" name="pais" status="optional" requiredField="2"/>
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
                       
        final NodeList nIdList = doc.getElementsByTagName("idField");
        final IdField id = parseIdField(nIdList);
        
        final NodeList nIdxNGramList = doc.getElementsByTagName("idxNGramField");
        final IndexedNGramField idxNGram =  parseIdxNGramField(nIdxNGramList);        
        
        final NodeList nExactList = doc.getElementsByTagName("exactField");
        final Set<ExactField> exact =  parseExactFields(nExactList);                
        
        final NodeList nNGramList = doc.getElementsByTagName("nGramField");
        final Set<NGramField> ngram = parseNGramFields(nNGramList);                
        
        final NodeList nScoreList = doc.getElementsByTagName("score");
        final Set<Score> scrs = parseScores(nScoreList);
        
        return new Parameters(id, idxNGram, exact, ngram, scrs);
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
        final IdField id = new IdField(
                                Integer.parseInt(eElement.getAttribute("pos")));
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
        final String name = eElement.getAttribute("name");
        if (name == null) {
            throw new IOException("missing 'name' attribute");
        }
        final String pos = eElement.getAttribute("pos");
        if (pos == null) {
            throw new IOException("missing 'pos' attribute");
        }
        final String minScore = eElement.getAttribute("minScore");
        if (minScore == null) {
            throw new IOException("missing 'minScore' attribute");
        }
        final String strMissingFields = 
                                 eElement.getAttribute("maxScoreIfMissingFlds");
        final Set<Integer> missingFields = new HashSet();
        if ((strMissingFields != null) && (!strMissingFields.trim().isEmpty())) {
            for (String fpos : strMissingFields.trim().split(" *\\, *")) {
                missingFields.add(Integer.valueOf(fpos));
            }
        }
        final IndexedNGramField idxNGram = new IndexedNGramField(
              name,
              Integer.parseInt(pos),
              Float.parseFloat(minScore),
              missingFields);
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
            final String name = eElement.getAttribute("name");
            if (name == null) {
                throw new IOException("missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos");
            if (pos == null) {
                throw new IOException("missing 'pos' attribute");
            }
            final String status = eElement.getAttribute("status");
            final String match = eElement.getAttribute("match");
            final String requiredField = eElement.getAttribute("requiredField");            
            final String minScore = eElement.getAttribute("minScore");
            if (minScore == null) {
                throw new IOException("missing 'minScore' attribute");
            }
            final NGramField nGram = new NGramField(
                  name,
                  Integer.parseInt(pos),
                  (status == null) ? true : !status.equals("required"),
                  (match == null) ? true : !match.equals("required"),
                  ((requiredField == null)||(requiredField.trim().isEmpty())) 
                                             ? -1 
                                             : Integer.parseInt(requiredField),
                  Float.parseFloat(minScore));
            ngramSet.add(nGram);
        }
        return ngramSet;
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
            final String name = eElement.getAttribute("name");
            if (name == null) {
                throw new IOException("missing 'name' attribute");
            }
            final String pos = eElement.getAttribute("pos");
            if (pos == null) {
                throw new IOException("missing 'pos' attribute");
            }
            final String status = eElement.getAttribute("status");
            final String match = eElement.getAttribute("match");
            final String requiredField = eElement.getAttribute("requiredField");
            final ExactField def = new ExactField(
                  name,
                  Integer.parseInt(pos),
                  (status == null) ? true : !status.equals("required"),
                  (match == null) ? true : !match.equals("required"),
                  ((requiredField == null)||(requiredField.trim().isEmpty())) 
                                             ? -1 
                                             : Integer.parseInt(requiredField));
            exactSet.add(def);
        }
        return exactSet;
    }
    
    static Set<Score> parseScores(final NodeList nScoreList) throws IOException {
        assert nScoreList != null;
            
        final int len = nScoreList.getLength();
        if (len == 0) {
            throw new IOException("empty score set");
        }        
        final Set<Score> scoreSet = new TreeSet<>();
        for (int idx = 0; idx < len; idx++) { 
            final Node nNode = nScoreList.item(idx);
            
            if (nNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new IOException("'score' is not an Element node");
            }
            final Element eElement = (Element) nNode;
            final Score score = new Score(
                          Float.parseFloat(eElement.getAttribute("minValue")),
                          Integer.parseInt(eElement.getAttribute("minFields")));
            scoreSet.add(score);
        }
        
        return scoreSet;
    }        
}
