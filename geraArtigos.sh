echo "Copia bases lil e spa do Chico para diretório local"
#cp -p  /bases/lnkG4/lnk.lil/spa.{mst,xrf} .

echo "Gera arquivos texto a partir das bases da dados"
# mx spa "pft=if p(v880) and p(v112) then v880,'|',v112,'|',v310,'|',v36,'|',v114,'|',v65[1].4,'|',v31[1],'|',v32[1]/ fi" lw=0 now > spa.txt
echo "fase 1"
./mx LILACS "pft=if p(v2) and p(v12) and v5.1='S' and v6='as' and p(v12[1]) then v2,'|',replace(v12[1]^*,'|',''),'|',v10^*,'|',v30^*,'|',v14^*,'|',v65[1].4,'|',v31,'|',v32/ fi" lw=0 now > lilArtigos0.txt
echo "fase 2"
./mx LILACS "pft=if p(v2) and p(v12) and v5.1='S' and v6='as' and p(v12[2]) then v2,'|',replace(v12[2]^*,'|',''),'|',v10^*,'|',v30^*,'|',v14^*,'|',v65[1].4,'|',v31,'|',v32/ fi" lw=0 now >> lilArtigos0.txt
echo "fase 3"
./mx LILACS "pft=if p(v2) and p(v12) and v5.1='S' and v6='as' and p(v12[3]) then v2,'|',replace(v12[3]^*,'|',''),'|',v10^*,'|',v30^*,'|',v14^*,'|',v65[1].4,'|',v31,'|',v32/ fi" lw=0 now >> lilArtigos0.txt
echo "fase 4"
./mx LILACS "pft=if p(v2) and p(v12) and v5.1='S' and v6='as' and p(v12[4]) then v2,'|',replace(v12[4]^*,'|',''),'|',v10^*,'|',v30^*,'|',v14^*,'|',v65[1].4,'|',v31,'|',v32/ fi" lw=0 now >> lilArtigos0.txt
echo "fase 5"
./mx LILACS "pft=if p(v2) and p(v12) and v5.1='S' and v6='as' and p(v13) then v2,'|',replace(v13,'|',''),'|',v10^*,'|',v30^*,'|',v14^*,'|',v65[1].4,'|',v31,'|',v32/ fi" lw=0 now >> lilArtigos0.txt
echo "sort"
sort -u lilArtigos0.txt > lilArtigos.txt
rm lilArtigos0.txt
