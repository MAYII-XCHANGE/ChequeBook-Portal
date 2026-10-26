const expectedHeaders = [
  'S/N',
  'Account Number',
  'Account Name',
  'Branch Name',
  'Branch Address',
  'Sort Code',
  'Cheque Type',
  'No of Leaves',
  'No of Booklets',
  'Delivery SolID',
  'Delivery Address',
  'Scheme Code',
  'Currency',
  'Range Start',
  'Range Stop',
];

function parseCsvRows(source) {
  const rows = [];
  let row = [];
  let cell = '';
  let quoted = false;
  for (let index = 0; index < source.length; index += 1) {
    const character = source[index];
    if (quoted) {
      if (character === '"' && source[index + 1] === '"') {
        cell += '"';
        index += 1;
      } else if (character === '"') quoted = false;
      else cell += character;
    } else if (character === '"') quoted = true;
    else if (character === ',') {
      row.push(cell);
      cell = '';
    } else if (character === '\n') {
      row.push(cell.replace(/\r$/, ''));
      rows.push(row);
      row = [];
      cell = '';
    } else cell += character;
  }
  if (quoted) throw new Error('The CSV contains an unterminated quoted value.');
  if (cell || row.length) {
    row.push(cell.replace(/\r$/, ''));
    rows.push(row);
  }
  return rows.filter((values) => values.some((value) => value.trim()));
}

export function parseVendorPrintResponseCsv(text) {
  const rows = parseCsvRows(String(text || '').replace(/^\uFEFF/, ''));
  if (rows.length < 2) throw new Error('The vendor response must contain a header and at least one data row.');
  const headers = rows[0].map((header) => header.trim());
  if (headers.length !== expectedHeaders.length
      || expectedHeaders.some((header, index) => headers[index] !== header)) {
    throw new Error(`The CSV headers must be: ${expectedHeaders.join(', ')}.`);
  }
  return rows.slice(1).map((values, index) => {
    if (values.length !== expectedHeaders.length)
      throw new Error(`CSV row ${index + 2} must contain ${expectedHeaders.length} columns.`);
    return {
      serialNumber: values[0].trim(),
      accountNumber: values[1].trim(),
      accountName: values[2].trim(),
      branchName: values[3].trim(),
      branchAddress: values[4].trim(),
      sortCode: values[5].trim(),
      chequeType: values[6].trim(),
      numberOfLeaves: Number(values[7].trim()),
      numberOfBooklets: Number(values[8].trim()),
      deliverySolId: values[9].trim(),
      deliveryAddress: values[10].trim(),
      schemeCode: values[11].trim(),
      currency: values[12].trim(),
      rangeStart: values[13].trim(),
      rangeStop: values[14].trim(),
    };
  });
}

export const vendorPrintResponseHeaders = expectedHeaders;
