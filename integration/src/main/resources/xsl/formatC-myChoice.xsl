<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="myChoiceSet">
    <classes>
      <xsl:for-each select="course">
        <class origin="C">
          <id><xsl:value-of select="Cno"/></id>
          <name><xsl:value-of select="Cnm"/></name>
          <time><xsl:value-of select="Ctm"/></time>
          <score><xsl:value-of select="Cpt"/></score>
          <teacher><xsl:value-of select="Tec"/></teacher>
          <location><xsl:value-of select="Pla"/></location>
          <share>Y</share>
          <sno><xsl:value-of select="Sno"/></sno>
          <grade><xsl:value-of select="Grd"/></grade>
        </class>
      </xsl:for-each>
    </classes>
  </xsl:template>
</xsl:stylesheet>
