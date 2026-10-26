const fs = require('fs');
const path = require('path');

const OPEN_END = '9999-12-31';
const FRACTION_DIGITS = { EUR: 2, USD: 2, GBP: 2, JPY: 0, KWD: 3 };

const file = path.join(process.env.SEED_DIR || '/seed', 'catalog.json');
const catalog = JSON.parse(fs.readFileSync(file, 'utf8'));

function minorUnits(product, price) {
  const digits = FRACTION_DIGITS[price.currency];
  if (digits === undefined) {
    throw new Error(`${product.key}: currency ${price.currency} is not in FRACTION_DIGITS`);
  }
  const scaled = price.value * Math.pow(10, digits);
  const minor = Math.round(scaled);
  if (price.value <= 0 || Math.abs(scaled - minor) > 1e-6) {
    throw new Error(`${product.key}: ${price.value} is not a valid ${price.currency} amount`);
  }
  return minor;
}

function checkPeriods(product) {
  const byCurrency = {};
  for (const price of product.prices) {
    const end = price.endDate || OPEN_END;
    if (!(price.initDate < end)) {
      throw new Error(`${product.key}: endDate must be after initDate in ${JSON.stringify(price)}`);
    }
    (byCurrency[price.currency] = byCurrency[price.currency] || []).push({ init: price.initDate, end: end });
  }
  for (const [currency, periods] of Object.entries(byCurrency)) {
    periods.sort((a, b) => a.init.localeCompare(b.init));
    for (let i = 1; i < periods.length; i++) {
      if (periods[i].init <= periods[i - 1].end) {
        throw new Error(`${product.key}: ${currency} prices starting ${periods[i - 1].init} and ${periods[i].init} overlap`);
      }
    }
  }
}

const products = db.getSiblingDB('products').getCollection('products');
for (const product of catalog.products) {
  checkPeriods(product);
  const document = {
    _id: ObjectId(product.id),
    name: product.name,
    description: product.description,
    prices: product.prices.map(price => ({
      amountMinor: NumberLong(String(minorUnits(product, price))),
      currency: price.currency,
      initDate: price.initDate,
      endDate: price.endDate || OPEN_END
    })),
    version: NumberLong(String(product.prices.length))
  };
  products.replaceOne({ _id: document._id }, document, { upsert: true });
}
print(`Loaded ${catalog.products.length} catalogue products from ${file}`);
