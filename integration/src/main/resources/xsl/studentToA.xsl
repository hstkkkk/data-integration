<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="students">
    <学生集>
      <xsl:for-each select="student">
        <学生>
          <学号><xsl:value-of select="id"/></学号>
          <姓名><xsl:value-of select="name"/></姓名>
          <性别><xsl:value-of select="sex"/></性别>
          <院系><xsl:value-of select="major"/></院系>
          <关联账户><xsl:value-of select="id"/></关联账户>
        </学生>
      </xsl:for-each>
    </学生集>
  </xsl:template>
</xsl:stylesheet>
