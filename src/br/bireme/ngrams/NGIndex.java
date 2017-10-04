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

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;

/**
 *
 * @author Heitor Barbieri
 * date: 20151013
 */
public class NGIndex {
    private final String name;
    private final String indexPath;
    private final Analyzer analyzer;
    
    public NGIndex(final String name,
                   final String indexPath,
                   final boolean search) throws IOException {
        this(name, indexPath, new NGAnalyzer(search));
    }
    
    public NGIndex(final String name,
                   final String indexPath,
                   final Analyzer analyzer) throws IOException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (indexPath == null) {
            throw new NullPointerException("indexPath");
        }
        if (analyzer == null) {
            throw new NullPointerException("analyzer");
        }        
        this.name = name;
        this.indexPath = new File(indexPath).getCanonicalPath();
        this.analyzer = analyzer;        
    }

    public String getName() {
        return name;
    }

    public IndexWriter getIndexWriter(final boolean append) throws IOException {
        return getIndexWriter(indexPath, analyzer, append);
    }

    public IndexSearcher getIndexSearcher() throws IOException {
        return getIndexSearcher(indexPath);
    }
    
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    private IndexWriter getIndexWriter(final String indexPath,
                                       final Analyzer analyzer,
                                       final boolean append)
                                                            throws IOException {
        assert indexPath != null;
        assert analyzer != null;

        final Directory directory = FSDirectory.open(
                                                  new File(indexPath).toPath());
        final IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        
        if (append) {
            cfg.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
        } else {
            //cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        }

        return new IndexWriter(directory, cfg);
    }

    private IndexSearcher getIndexSearcher(final String indexPath)
                                                            throws IOException {

        final DirectoryReader ireader = DirectoryReader.open(
                                //FSDirectory.open(new File(indexPath).toPath()));
                               new MMapDirectory(new File(indexPath).toPath()));
                               //new RAMDirectory(FSDirectory.open(new File(indexPath).toPath()), IOContext.DEFAULT));
                               //new RAMDirectory(FSDirectory.open(new File(indexPath).toPath()), IOContext.READONCE));

        return new IndexSearcher(ireader);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.name);
        hash = 97 * hash + Objects.hashCode(this.indexPath);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NGIndex other = (NGIndex) obj;
        if (!this.name.equals(other.name)) {
            return false;
        }
        return this.indexPath.equals(other.indexPath);
    }        
}
