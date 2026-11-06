<#-- Under the form: back to the shop (the base URL of the client that asked for the login: the BFF). -->
<#macro content>
  <#if client?? && client.baseUrl?has_content>
    <p class="comptoir-back">
      <a id="comptoir-back" href="${client.baseUrl}">${msg("backToShop")}</a>
    </p>
  </#if>
</#macro>
