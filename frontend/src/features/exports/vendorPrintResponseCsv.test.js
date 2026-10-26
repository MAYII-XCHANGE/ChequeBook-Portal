import { describe, expect, it } from 'vitest';
import { parseVendorPrintResponseCsv } from './vendorPrintResponseCsv';

describe('vendor print response CSV', () => {
  it('preserves leading zeroes and parses a valid allocation', () => {
    const rows = parseVendorPrintResponseCsv([
      'S/N,Account Number,Account Name,Branch Name,Branch Address,Sort Code,Cheque Type,No of Leaves,No of Booklets,Delivery SolID,Delivery Address,Scheme Code,Currency,Range Start,Range Stop',
      '1,0123456789,"Customer, Limited",Victoria Island,1 Bank Street,001,01,50,1,001,Victoria Island,CUR,NGN,001001,001050',
    ].join('\r\n'));

    expect(rows).toEqual([{
      serialNumber: '1', accountNumber: '0123456789', accountName: 'Customer, Limited',
      branchName: 'Victoria Island', branchAddress: '1 Bank Street', sortCode: '001', chequeType: '01',
      numberOfLeaves: 50, numberOfBooklets: 1, deliverySolId: '001',
      deliveryAddress: 'Victoria Island', schemeCode: 'CUR', currency: 'NGN',
      rangeStart: '001001', rangeStop: '001050',
    }]);
  });

  it('rejects an unexpected contract', () => {
    expect(() => parseVendorPrintResponseCsv('RequestId,ChequeType\nCBR-1,01'))
      .toThrow(/headers must be/i);
  });
});
