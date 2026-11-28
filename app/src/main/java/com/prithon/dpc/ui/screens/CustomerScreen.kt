package com.prithon.dpc.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prithon.dpc.ui.components.StatusPill
import com.prithon.dpc.ui.theme.Blue600
import com.prithon.dpc.ui.theme.PureWhite
import com.prithon.dpc.ui.theme.Slate200
import com.prithon.dpc.ui.theme.Slate500
import com.prithon.dpc.ui.viewmodel.DpcUiState

@Composable
fun CustomerScreen(
    state: DpcUiState,
    onSaveCustomer: (
        tenantCode: String,
        name: String,
        phone: String,
        imei: String,
        loanId: String,
        emiAmount: String
    ) -> Boolean,
    onSyncNow: () -> Unit
) {
    val customer = state.customer
    var isEditing by rememberSaveable { mutableStateOf(!customer.isRegistered) }

    var tenantCode by rememberSaveable { mutableStateOf(customer.tenantCode) }
    var customerName by rememberSaveable { mutableStateOf(customer.customerName) }
    var customerPhone by rememberSaveable { mutableStateOf(customer.customerPhone) }
    var enteredImei by rememberSaveable { mutableStateOf(customer.enteredImei) }
    var loanId by rememberSaveable { mutableStateOf(customer.loanId) }
    var emiAmount by rememberSaveable { mutableStateOf(customer.emiAmount) }

    var tenantError by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneError by rememberSaveable { mutableStateOf<String?>(null) }
    var imeiError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(customer) {
        if (!isEditing) {
            tenantCode = customer.tenantCode
            customerName = customer.customerName
            customerPhone = customer.customerPhone
            enteredImei = customer.enteredImei
            loanId = customer.loanId
            emiAmount = customer.emiAmount
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (customer.isRegistered && !isEditing) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bound Customer Record",
                                style = MaterialTheme.typography.titleLarge
                            )
                            StatusPill(
                                text = customer.deviceStatus,
                                isPositive = customer.deviceStatus == "NORMAL"
                            )
                        }

                        HorizontalDivider(color = Slate200)

                        RecordField("Shop / Tenant Code", customer.tenantCode.ifEmpty { "Unassigned" })
                        RecordField("Customer Name", customer.customerName)
                        RecordField("Phone Number", customer.customerPhone)
                        RecordField("15-Digit IMEI", customer.enteredImei.ifEmpty { "Hardware Bound" })
                        RecordField("Loan ID", customer.loanId.ifEmpty { "Standard Finance" })
                        RecordField(
                            "Monthly EMI",
                            if (customer.emiAmount.isNotEmpty()) "₹${customer.emiAmount}" else "N/A"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    tenantCode = customer.tenantCode
                                    customerName = customer.customerName
                                    customerPhone = customer.customerPhone
                                    enteredImei = customer.enteredImei
                                    loanId = customer.loanId
                                    emiAmount = customer.emiAmount
                                    isEditing = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("EDIT")
                            }
                            Button(
                                onClick = onSyncNow,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("SYNC")
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (customer.isRegistered) "Update Customer Binding" else "Customer & EMI Onboarding",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Bind this handset to your retailer shop code and customer EMI agreement.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500
                        )

                        OutlinedTextField(
                            value = tenantCode,
                            onValueChange = {
                                tenantCode = it.uppercase()
                                tenantError = null
                            },
                            label = { Text("Shop / Tenant Code *") },
                            placeholder = { Text("APX-4021") },
                            leadingIcon = { Icon(Icons.Rounded.Storefront, contentDescription = null, tint = Blue600) },
                            isError = tenantError != null,
                            supportingText = tenantError?.let { { Text(it) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customerName,
                            onValueChange = {
                                customerName = it
                                nameError = null
                            },
                            label = { Text("Customer Full Name *") },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = Blue600) },
                            isError = nameError != null,
                            supportingText = nameError?.let { { Text(it) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = {
                                customerPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(13)
                                phoneError = null
                            },
                            label = { Text("Customer Mobile Number *") },
                            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null, tint = Blue600) },
                            isError = phoneError != null,
                            supportingText = phoneError?.let { { Text(it) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = enteredImei,
                            onValueChange = {
                                enteredImei = it.filter { ch -> ch.isDigit() }.take(15)
                                imeiError = null
                            },
                            label = { Text("15-Digit Device IMEI *") },
                            leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null, tint = Blue600) },
                            isError = imeiError != null,
                            supportingText = {
                                Text(imeiError ?: "${enteredImei.length}/15 digits")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = loanId,
                                onValueChange = { loanId = it },
                                label = { Text("Loan ID") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = emiAmount,
                                onValueChange = { emiAmount = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Monthly EMI (₹)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (customer.isRegistered) {
                                OutlinedButton(
                                    onClick = { isEditing = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("CANCEL")
                                }
                            }
                            Button(
                                onClick = {
                                    var valid = true
                                    if (tenantCode.isBlank()) {
                                        tenantError = "Shop Code is required"
                                        valid = false
                                    }
                                    if (customerName.isBlank()) {
                                        nameError = "Customer Name is required"
                                        valid = false
                                    }
                                    if (customerPhone.isBlank()) {
                                        phoneError = "Phone Number is required"
                                        valid = false
                                    }
                                    if (enteredImei.length != 15) {
                                        imeiError = "Valid 15-digit IMEI is required"
                                        valid = false
                                    }
                                    if (valid) {
                                        val saved = onSaveCustomer(
                                            tenantCode,
                                            customerName,
                                            customerPhone,
                                            enteredImei,
                                            loanId,
                                            emiAmount
                                        )
                                        if (saved) isEditing = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("BIND & SYNC")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Slate500
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
