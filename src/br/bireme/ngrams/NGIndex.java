/*=========================================================================

    NGrams © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/NGrams/blob/master/LICENSE.txt

  ==========================================================================*/

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
    private IndexWriter writer;

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
        this.writer = getIndexWriter(indexPath, analyzer);        
    }
    
    public void close() {
        if (writer != null) {
            try {
                writer.close();
                writer = null;
            } catch(IOException ioe) {}
        }
    }

    public String getName() {
        return name;
    }

    public IndexWriter getIndexWriter() throws IOException {
        if ((writer == null) || (!writer.isOpen())) {
            writer = getIndexWriter(indexPath, analyzer);
        }
        return writer;
    }

    public IndexSearcher getIndexSearcher() throws IOException {
        return getIndexSearcher(indexPath);
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    private IndexWriter getIndexWriter(final String indexPath,
                                       final Analyzer analyzer)
                                                            throws IOException {
        assert indexPath != null;
        assert analyzer != null;

        new File(indexPath, "write.lock").delete();
        
        final File dir = new File(indexPath);
        final Directory directory = FSDirectory.open(dir.toPath());
        final IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

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
