<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="choices">
    <选课集>
      <xsl:for-each select="choice">
        <选课>
          <课程编号><xsl:value-of select="cid"/></课程编号>
          <学号><xsl:value-of select="sid"/></学号>
          <得分><xsl:value-of select="score"/></得分>
        </选课>
      </xsl:for-each>
    </选课集>
  </xsl:template>
</xsl:stylesheet>
