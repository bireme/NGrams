echo "Gera arquivo a ser indexado usando o MEDLINE"

./mx /bases/mdlG4/m6615.mdl/mdlbb6615 pft="s1:=(if p(v374) then replace(v374,'|',' ') else replace(v372^*,'|',' ') fi),if p(v352) then MEDLINE',replace(v969,'|',' '),'|',replace(v352,'|',' '),'|',s1,'|',replace(v305,'|',' '),'|',replace(v353,'|',' '),'|',replace(v354[1].4,'|',' '),'|',replace(v381,'|',' '),'|',replace(v373,'|',' '),'|S|as'/ fi" lw=0 now > medline.txt
