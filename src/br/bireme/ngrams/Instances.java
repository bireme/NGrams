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
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.lucene.analysis.Analyzer;
import org.xml.sax.SAXException;

/**
 *
 * @author Heitor Barbieri
 * date: 20150721
 */
class Instances {
    private static final Map<String, NGInstance> instances = new HashMap<>();    
    private static final Analyzer analyzer = new NGAnalyzer();
    
    static void addInstance(final String indexPath,
                            final String indexAlias,
                            final String confFile,
                            final String confFileEncoding) throws IOException, 
                                                   ParserConfigurationException, 
                                                   SAXException {
        assert indexPath != null;
        assert indexAlias != null;
        assert confFile != null;
        assert confFileEncoding != null;
        
        final NGInstance instance = new NGInstance(indexPath, analyzer, confFile,
                                                              confFileEncoding);
        instances.put(indexAlias, instance);
    }
    
    static NGInstance getInstance(final String name) {
        assert name != null;
        
        final NGInstance instance = instances.get(name);
        
        if (instance == null) {
            throw new IllegalArgumentException("Invalid index name: " + name);
        }
        return instance;
    }
    
    static Analyzer getAnalyzer() {
        return analyzer;
    }
}
