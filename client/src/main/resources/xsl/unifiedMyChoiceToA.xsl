<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="classes">
    <课程集>
      <xsl:for-each select="class">
        <课程>
          <来源><xsl:value-of select="@origin"/></来源>
          <课程编号><xsl:value-of select="id"/></课程编号>
          <课程名称><xsl:value-of select="name"/></课程名称>
          <课时><xsl:value-of select="time"/></课时>
          <学分><xsl:value-of select="score"/></学分>
          <授课老师><xsl:value-of select="teacher"/></授课老师>
          <授课地点><xsl:value-of select="location"/></授课地点>
          <学生编号><xsl:value-of select="sno"/></学生编号>
          <成绩><xsl:value-of select="grade"/></成绩>
        </课程>
      </xsl:for-each>
    </课程集>
  </xsl:template>
</xsl:stylesheet>
