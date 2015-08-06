echo "Copia bases lil e spa do Chico para diretório local"
#cp -p  /bases/lnkG4/lnk.lil/spa.{mst,xrf} .

echo "Gera arquivos texto a partir das bases da dados"
# mx spa "pft=if p(v880) and p(v112) then v880,'|',v112,'|',v310,'|',v36,'|',v114,'|',v65[1].4,'|',v31[1],'|',v32[1]/ fi" lw=0 now > spa.txt
./mx LILACS "pft=if p(v2) and p(v12) and v5.1='S' and v6='as' then v2,'|',replace(v12,'|',''),'|',v10^*,'|',v30^*,'|',v14^*,'|',v65[1].4,'|',v31,'|',v32/ fi" lw=0 now > lilArtigos.txt

