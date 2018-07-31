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
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 *
 * @author Heitor Barbieri
 * date: 20151013
 */
public class NGSchema {
    private final String name;
    private final String config;
    private final Parameters parameters;
    private final Map<Integer,String> posNames;
    private final Map<String,Integer> namesPos;

    public NGSchema(final String name,
                    final String confFile,
                    final String confFileEncoding) throws
                                                   IOException,
                                                   ParserConfigurationException,
                                                   SAXException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (confFile == null) {
            throw new NullPointerException("confFile");
        }
        if (confFileEncoding == null) {
            throw new NullPointerException("confFileEncoding");
        }
        this.name = name;
        this.config = readFile(confFile, confFileEncoding);
        this.parameters = ParameterParser.parseParameters(this.config);
        this.posNames = new TreeMap<>();
        this.namesPos = new TreeMap<>();

        for (Field field : parameters.sfields.values()) {
            posNames.put(field.pos, field.name);
            namesPos.put(field.name, field.pos);
        }
    }
    
    public NGSchema(final String name,
                    final String content) throws IOException,
                                                 ParserConfigurationException,
                                                 SAXException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.name = name;
        this.config = content;
        this.parameters = ParameterParser.parseParameters(this.config);
        this.posNames = new TreeMap<>();
        this.namesPos = new TreeMap<>();

        for (Field field : parameters.sfields.values()) {
            posNames.put(field.pos, field.name);
            namesPos.put(field.name, field.pos);
        }
    }

    public String getName() {
        return name;
    }

    public Map<Integer,String> getPosNames() {
        return posNames;
    }

    public Map<String,Integer> getNamesPos() {
        return namesPos;
    }

    public String getConfig() {
        return config;
    }

    public String getSchemaJson() {
        final StringBuilder builder = new StringBuilder();
        final Collection<Field> flds = parameters.nameFields.values();
        final Map<Integer,Field> map = new TreeMap<>();
        boolean first = true;

        for (Field fld: flds) map.put(fld.pos, fld);
        final Collection<Field> fields = map.values();
        
        builder.append("{");
        builder.append("\"name\":\"");
        builder.append(name);
        builder.append("\",\"total\":");
        builder.append(fields.size());
        builder.append(",\"params\":[");
        for (Field fld: fields) {
            if (first) {
                first = false;
            } else {
                builder.append(",");
            }
            builder.append("{");
            builder.append("\"pos\":");
            builder.append(fld.pos);
            builder.append(",\"name\":\"");
            builder.append(fld.name);
            
            builder.append("\",\"type\":\"");
            if (fld instanceof IdField) {
                builder.append("idField");
            } else if (fld instanceof DatabaseField) {
                builder.append("databaseField");
            } else if (fld instanceof NoCompareField) {
                builder.append("NoCompField");
            } else if (fld instanceof ExactField) {
                builder.append("exactField");
            } else if (fld instanceof RegExpField) {
                builder.append("regExpField");
            } else if (fld instanceof IndexedNGramField) {
                builder.append("idxNGramField");
            } else if (fld instanceof NGramField) {
                builder.append("nGramField");
            }
            builder.append("\",\"requiredField\":\"");
            builder.append(fld.requiredField);
            builder.append("\",\"contentMatch\":\"");
            builder.append(fld.contentMatch.name().toLowerCase());
            builder.append("\"}");            
        }
        builder.append("]}");

        return builder.toString();
    }
    
    public String getSchemaXml() {
        String ret = "<config>";

        for (Score score: parameters.scores) {
            ret += "\n\t<score minValue=\"" + score.minValue + "\" minFields=\"" +
            score.minFields + "\"/>";
        }
        ret += "\n\t<databaseField pos=\"" + parameters.db.pos + "\"/>";
        ret += "\n\t<idField pos=\"" + parameters.id.pos + "\"/>";
        ret += "\n\t<idxNGramField pos=\"" + parameters.indexed.pos + "\" name=\"" +
                parameters.indexed.name + "\"/>";
        
        for (ExactField field: parameters.exacts) {
            ret += "\n\t<exactField pos=\"" + field.pos + "\" name=\"" +
                   field.name + "\" requiredField=\"" + field.requiredField +
                   "\" match=\"" + field.contentMatch.name() + "\"/>";
        }
        for (NGramField field: parameters.ngrams) {
            ret += "\n\t<nGramField pos=\"" + field.pos + "\" name=\"" +
                   field.name + "\" minScore=\"" + field.minScore + 
                   "\" match=\"" + field.contentMatch.name() + "\"/>";
        }
        for (RegExpField field: parameters.regexps) {
            ret += "\n\t<regExpField pos=\"" + field.pos + "\" name=\"" +
                   field.name + "\" requiredField=\"" + field.requiredField +
                   "\" match=\"" + 
                   field.contentMatch.name() + "\" pattern=\"" + field.matcher.
                   pattern().pattern() + "\" groupNum=\"" + field.groupNum + "\"/>";
        }
        for (NoCompareField field: parameters.nocompare) {
            ret += "\n\t<noCompField pos=\"" + field.pos + "\" name=\"" +
                   field.name + "\"/>";
        }
        ret += "\n</config>";
        
        return ret;
    }

    public String getIndexedFldName() {
        
        return parameters.indexed.name;
    }

    public Parameters getParameters() {
        return parameters;
    }

    static String readFile(final String confFile,
                           final String confFileEncoding) throws IOException {
        if (confFile == null) {
            throw new NullPointerException("confFile");
        }
        if (confFileEncoding == null) {
            throw new NullPointerException("confFileEncoding");
        }

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

    @Override
    public int hashCode() {
        int hash = 5;

        return 97 * hash + Objects.hashCode(this.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NGSchema other = (NGSchema) obj;

        return Objects.equals(this.name, other.name);
    }
}
