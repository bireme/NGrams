echo "Gera LILACS Producao Cientifica da Secretaria de Saude/SP (ses_prod)"
/usr/local/bireme/cisis/5.7c/linux64/lindG4/mx /bases/lilG4/ses.lil/LILACS  "proc=@../ses_ProducaoCientifica.prc" iso=ses_prod.iso -all now
/usr/local/bireme/cisis/5.7c/linux64/lindG4/mx iso=ses_prod.iso create=ses_prod -all now
rm ses_prod.iso

echo "Gera arquivo a ser pesquisado a partir da ses_prod"
/usr/local/bireme/cisis/5.7c/linux64/lindG4/mx ses_prod "pft=s1:=(if v5.1='S' or v5='M' then if p(v10) then (|//@//|+v10^*) else (|//@//|+v11^*) fi else (|//@//|+v16^*) fi),s3:=(if v5.1='S' then v30^* else v62^* fi),(if p(v12) then 'SES|',v2[1],'|',replace(v12^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1] fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi),(if p(v13) then 'SES|',v2[1],'|',replace(v13^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1] fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi),if a(v12) then (if p(v18) then 'SES|',v2[1],'|',replace(v18^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1]^* fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi) fi, if a(v12) then (if p(v19) then 'SES|',v2[1],'|',replace(v19^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1]^* fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1]/ fi) fi" lw=0 now > ses_prod.txt
rm ses_prod.mst ses_prod.xrf

