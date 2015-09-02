echo "Gera LILACS Producao Cientifica da Secretaria de Saude/SP (ses_prod)"
mx ../LILACS "proc=@ses_ProducaoCientifica.prc" iso=ses_prod.iso -all now
mx iso=ses_prod.iso create=ses_prod -all now
rm ses_prod.iso

echo "Gera arquivo a ser pesquisado a partir da ses_prod"
mx ses_prod "pft=s1:=(if v5.1='S' or v5='M' then (v10^*) else (v16^*) fi),s3:=(if v5.1='S' then v30^* else v62^* fi),(if p(v12) then v2[1],'|',replace(v12^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1] fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1],'|SES'/ fi),(if p(v13) then v2[1],'|',replace(v13^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1] fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1],'|SES'/ fi), if a(v12) then (if p(v18) then v2[1],'|',replace(v18^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*)  then v14[1]^* else v20[1]^* fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1],'|SES'/ fi) fi, if a(v12) then (if p(v19) then v2[1],'|',replace(v19^*,'|',''),'|',s1,'|',s3,'|',if p(v14[1]^*) then v14[1]^* else v20[1]^* fi,'|',v65[1].4,'|',v31[1],'|',v32[1],'|',v5[1],'|',v6[1],'|SES'/ fi) fi" lw=0 now > ses_prod.txt
rm ses_prod.mst ses_prod.xrf

