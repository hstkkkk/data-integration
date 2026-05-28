<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

  <xsl:template match="/">
    <classes>
      <xsl:apply-templates select="课程集/课程 | courses/course"/>
    </classes>
  </xsl:template>

  <xsl:template match="课程">
    <class>
      <id><xsl:value-of select="课程编号 | 编号"/></id>
      <name><xsl:value-of select="课程名称 | 名称"/></name>
      <time><xsl:value-of select="课时"/></time>
      <score><xsl:value-of select="学分"/></score>
      <teacher><xsl:value-of select="授课老师 | 老师"/></teacher>
      <location><xsl:value-of select="授课地点 | 地点"/></location>
      <share><xsl:value-of select="共享"/></share>
      <origin>
        <xsl:choose>
          <xsl:when test="课程编号">A</xsl:when>
          <xsl:otherwise>B</xsl:otherwise>
        </xsl:choose>
      </origin>
    </class>
  </xsl:template>

  <xsl:template match="course">
    <class>
      <id><xsl:value-of select="Cno"/></id>
      <name><xsl:value-of select="Cnm"/></name>
      <time><xsl:value-of select="Ctm"/></time>
      <score><xsl:value-of select="Cpt"/></score>
      <teacher><xsl:value-of select="Tec"/></teacher>
      <location><xsl:value-of select="Pla"/></location>
      <share><xsl:value-of select="Share"/></share>
      <origin>C</origin>
    </class>
  </xsl:template>
</xsl:stylesheet>
