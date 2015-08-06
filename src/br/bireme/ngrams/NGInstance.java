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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.xml.sax.SAXException;

/**
 *
 * @author Heitor Barbieri
 * date: 20150721
 */
class NGInstance {
    private final String indexPath;
    private final Analyzer analyzer;
    private final Parameters parameters;
    private final String config;
     
    NGInstance(final String indexPath,
               final Analyzer analyzer,
               final String confFile,
               final String confFileEncoding) throws IOException, 
                                                   ParserConfigurationException, 
                                                   SAXException {
        if (indexPath == null) {
            throw new NullPointerException("indexPath");
        }
        if (analyzer == null) {
            throw new NullPointerException("analyzer");
        }
        if (confFile == null) {
            throw new NullPointerException("confFile");
        }
        if (confFileEncoding == null) {
            throw new NullPointerException("confFileEncoding");
        }
        this.indexPath = indexPath;
        this.analyzer = analyzer;
        this.config = readFile(confFile, confFileEncoding);
        this.parameters = ParameterParser.parseParameters(this.config);
    }
    
    IndexWriter getIndexWriter() throws IOException {
        return getIndexWriter(indexPath, analyzer);
    }
    
    IndexSearcher getIndexSearcher() throws IOException {
        return getIndexSearcher(indexPath);
    }
    
    Parameters getParameters() {
        return parameters;
    }
    
    String getConfig() {
        return config;
    }   
    
    private static IndexWriter getIndexWriter(final String indexPath,
                                              final Analyzer analyzer) 
                                                            throws IOException {
        assert indexPath != null;
        assert analyzer != null;
        
        final Directory directory = FSDirectory.open(
                                                  new File(indexPath).toPath());
        final IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
    
        return new IndexWriter(directory, cfg);
    }
    
    private static IndexSearcher getIndexSearcher(final String indexPath) 
                                                            throws IOException {
         
        final DirectoryReader ireader = DirectoryReader.open(
                                FSDirectory.open(new File(indexPath).toPath()));
        
        return new IndexSearcher(ireader);
    }
    
    static String readFile(final String confFile,
                           final String confFileEncoding) throws IOException {
        assert confFile != null;
        assert confFileEncoding != null;
        
        final Charset charset = Charset.forName(confFileEncoding);
        final StringBuilder builder = new StringBuilder();
        boolean first = true;
        
        try (BufferedReader reader = Files.newBufferedReader(
                                          new File(confFile).toPath(), 
                                                                     charset)) {                     
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (first) {
                    first = false;
                } else {
                    builder.append("\n");
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
