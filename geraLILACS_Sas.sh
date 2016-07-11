echo "Gera arquivo a ser indexado (S/as) a partir da LILACS"
echo "<database>|<id>|<autores>|<titulo_artigo>|<titulo_revista>|<ano_publicacao>|<volume>|<numero_fasciculo>|<pagina_inicial>"


/usr/local/bireme/cisis/5.7c/linux64/lindG4/mx /bases/lilG4/lil.lil/LILACS  "pft=if v5.1='S' then s1:=(if p(v10) then (|//@//|+v10^*) else (|//@//|+v11^*) fi), s3:=(v30^*), (if p(v12) then 'LILACS_Sas|',v2[1],'|',replace(v12^*,'|',''),'|',s1,'|',s3,'|',v65[1].4,'|',v31[1],'|',v32[1]'|',if p(v14[1]^*) then v14[1]^* fi,/ fi),(if p(v13) then 'LILACS_Sas|',v2[1],'|',replace(v13^*,'|',''),'|',s1,'|',s3,'|',v65[1].4,'|',v31[1],'|',v32[1]'|',if p(v14[1]^*) then v14[1]^* fi/ fi),fi" lw=0 tell=50000 now > LILACS_Sas.txt
