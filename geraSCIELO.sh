echo "Gera arquivo a ser indexado a partir da bib4cit1s1.LG4"

/usr/local/bireme/cisis/5.7c/linux64/lindG4/mx ../../bib4cits1.LG4 "pft=s1:=((v10^s,x1,v10^n)),s3:=((v30^*)),(if p(v12) then 'SCIELO_',v880[1]^c,'|',v880[1]^*,'|',replace(v12^*,'|',''),'|',replace(s1,'|',''),'|',replace(s3,'|',''),'|',replace(v14[1]^*,'|',''),'|',replace(v65[1].4,'|',''),'|',replacE(v31[1],'|',''),'|',replace(v32[1],'|',''),'|S|as|'/ fi)" lw=0 tell=50000 now > scielo.txt
