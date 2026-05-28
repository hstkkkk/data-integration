<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="classes">
    <courses>
      <xsl:for-each select="class">
        <course>
          <Org><xsl:value-of select="@origin"/></Org>
          <Cno><xsl:value-of select="id"/></Cno>
          <Cnm><xsl:value-of select="name"/></Cnm>
          <Ctm><xsl:value-of select="time"/></Ctm>
          <Cpt><xsl:value-of select="score"/></Cpt>
          <Tec><xsl:value-of select="teacher"/></Tec>
          <Pla><xsl:value-of select="location"/></Pla>
          <Sno><xsl:value-of select="sno"/></Sno>
          <Grd><xsl:value-of select="grade"/></Grd>
        </course>
      </xsl:for-each>
    </courses>
  </xsl:template>
</xsl:stylesheet>
