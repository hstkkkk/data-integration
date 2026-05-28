<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

  <xsl:template match="/">
    <students>
      <xsl:apply-templates select="学生集/学生 | students/student"/>
    </students>
  </xsl:template>

  <xsl:template match="学生">
    <student>
      <id><xsl:value-of select="学号"/></id>
      <name><xsl:value-of select="姓名"/></name>
      <sex><xsl:value-of select="性别"/></sex>
      <major><xsl:value-of select="院系 | 专业"/></major>
      <origin>
        <xsl:choose>
          <xsl:when test="院系">A</xsl:when>
          <xsl:otherwise>B</xsl:otherwise>
        </xsl:choose>
      </origin>
    </student>
  </xsl:template>

  <xsl:template match="student">
    <student>
      <id><xsl:value-of select="Sno"/></id>
      <name><xsl:value-of select="Snm"/></name>
      <sex><xsl:value-of select="Sex"/></sex>
      <major><xsl:value-of select="Sde"/></major>
      <origin>C</origin>
    </student>
  </xsl:template>
</xsl:stylesheet>
