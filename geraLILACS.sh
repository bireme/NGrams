HOME=/home/javaapps/NGrams

echo "Copia LILACS atualizada do serverabd2"
scp -p transfer@serverabd2.bireme.br:/home/lilacs/www/bases/lildbi/dbcertif/lilacs/LILACS.{mst,xrf} $HOME/work/

echo "Gera arquivo a ser indexado a partir da LILACS"
echo "<database>|<id>|<titulo_artigo>|<autores>|<titulo_revista>|<pagina_inicial>|<ano_publicacao>|<volume>|<numero_fasciculo>|<tipo_literatura>|<nivel_tratamento>"

/usr/local/bireme/cisis/5.7c/linux64/lindG4/mx $HOME/work/LILACS "pft=s1:=(if v5.1='S' or v5='M' then if p(v10) then (|//@//|+v10^*) else (|//@//|+v11^*) fi else (|//@//|+v16^*) fi),s3:=(if v5.1='S' then v30^* else v62^* fi),(if p(v12) then 'LILACS|',v2[1],'|',replace(v12^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1] fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi),(if p(v13) then 'LILACS|',v2[1],'|',replace(v13^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1] fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi),if a(v12) then (if p(v18) then 'LILACS|',v2[1],'|',replace(v18^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1]^* fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi) fi, if a(v12) then (if p(v19) then 'LILACS|',v2[1],'|',replace(v19^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1]^* fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi) fi" lw=0 tell=50000 now > $HOME/LILACS.txt

rm $HOME/work/LILACS.{mst,xrf}
 
