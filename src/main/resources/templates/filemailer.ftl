<#-- Greet the user with his/her name -->
<#assign aDateTime = .now>
<#assign aDate = aDateTime?date>
<#assign aTime = aDateTime?time>
<html>
  <head>
    <title>File: ${bkpFile} ${aDate} ${aTime}</title>
  </head>
  <body>
 <h1> Welcome ${to} ! </h1>
  <ul>
    <li>Your bakup file: ${bkpFile}</li>
  </ul>
  </body>
  </html>

  