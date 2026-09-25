"""Generates a Postman v2.1 collection from AccountFlow's live OpenAPI document.

Driving it off /v3/api-docs means the collection cannot drift from the API:
re-run it after any endpoint change and the collection regenerates.
"""
import json, re, collections

SPEC = json.load(open('/tmp/openapi.json'))
SCHEMAS = SPEC['components']['schemas']

# ---------------------------------------------------------------- example bodies

def resolve(schema, depth=0):
    """Turns a JSON-Schema node into a concrete example value."""
    if depth > 6 or not isinstance(schema, dict):
        return None
    if '$ref' in schema:
        name = schema['$ref'].rsplit('/', 1)[-1]
        return resolve(SCHEMAS.get(name, {}), depth + 1)
    if 'example' in schema:
        ex = schema['example']
        if isinstance(ex, str) and ex.strip().startswith('{'):
            try:
                return json.loads(ex)
            except Exception:
                return ex
        return ex
    t = schema.get('type')
    if t == 'object' or 'properties' in schema:
        return {k: resolve(v, depth + 1) for k, v in schema.get('properties', {}).items()}
    if t == 'array':
        item = resolve(schema.get('items', {}), depth + 1)
        return [item] if item is not None else []
    if 'enum' in schema:
        return schema['enum'][0]
    return {'string': 'string', 'integer': 0, 'number': 0, 'boolean': False}.get(t)


def request_example(op):
    body = op.get('requestBody')
    if not body:
        return None
    schema = body.get('content', {}).get('application/json', {}).get('schema')
    return resolve(schema) if schema else None


def envelope(data, paginated=False, message=None):
    out = {'success': True, 'data': data}
    if paginated:
        out['pagination'] = {'page': 0, 'size': 20, 'totalElements': 1, 'totalPages': 1}
    if message:
        out['message'] = message
    out['requestId'] = '0f9c1a52-8f1e-4a7b-9a3d-2c6b5e4d1a88'
    return out


# The envelope is generic, so the payload type per operation is inferred here.
RESPONSE_DATA = {
    'post /api/v1/auth/register': ('TokenResponse', False),
    'post /api/v1/auth/login': ('TokenResponse', False),
    'post /api/v1/auth/refresh': ('TokenResponse', False),
    'post /api/v1/auth/logout': (None, False),
    'get /api/v1/users/me': ('UserResponse', False),
    'put /api/v1/users/me': ('UserResponse', False),
    'post /api/v1/accounts': ('AccountResponse', False),
    'get /api/v1/accounts': ('AccountResponse', 'list'),
    'get /api/v1/accounts/{accountId}': ('AccountResponse', False),
    'put /api/v1/accounts/{accountId}': ('AccountResponse', False),
    'patch /api/v1/accounts/{accountId}/status': ('AccountResponse', False),
    'post /api/v1/accounts/{accountId}/transactions/credit': ('TransactionResponse', False),
    'post /api/v1/accounts/{accountId}/transactions/debit': ('TransactionResponse', False),
    'get /api/v1/accounts/{accountId}/transactions': ('TransactionResponse', True),
    'get /api/v1/transactions': ('TransactionResponse', True),
    'get /api/v1/transactions/{transactionId}': ('TransactionResponse', False),
    'post /api/v1/transfers': ('TransferResponse', False),
    'get /api/v1/transfers': ('TransferResponse', True),
    'get /api/v1/transfers/{transferId}': ('TransferResponse', False),
    'post /api/v1/transfers/bank-to-cash': ('TransferResponse', False),
    'post /api/v1/transfers/cash-to-bank': ('TransferResponse', False),
    'get /api/v1/cash': ('CashResponse', False),
    'post /api/v1/cash/deposit': ('TransactionResponse', False),
    'post /api/v1/cash/withdraw': ('TransactionResponse', False),
    'get /api/v1/cash/transactions': ('TransactionResponse', True),
}

# --------------------------------------------------------------- capture scripts

CAPTURE = {
    'post /api/v1/auth/register': """const b = pm.response.json();
if (b.success) {
  pm.collectionVariables.set('accessToken', b.data.accessToken);
  pm.collectionVariables.set('refreshToken', b.data.refreshToken);
  pm.collectionVariables.set('userId', b.data.user.id);
}
pm.test('registered', () => pm.response.to.have.status(201));""",
    'post /api/v1/auth/login': """const b = pm.response.json();
if (b.success) {
  pm.collectionVariables.set('accessToken', b.data.accessToken);
  pm.collectionVariables.set('refreshToken', b.data.refreshToken);
  pm.collectionVariables.set('userId', b.data.user.id);
}
pm.test('signed in', () => pm.response.to.have.status(200));""",
    'post /api/v1/auth/refresh': """const b = pm.response.json();
if (b.success) {
  // The presented token is now revoked - store the rotated one or the next
  // refresh will look like token theft and revoke the whole family.
  pm.collectionVariables.set('accessToken', b.data.accessToken);
  pm.collectionVariables.set('refreshToken', b.data.refreshToken);
}""",
    'post /api/v1/accounts': """const b = pm.response.json();
if (b.success) pm.collectionVariables.set('accountId', b.data.id);
pm.test('account created', () => pm.response.to.have.status(201));""",
    'post /api/v1/accounts/{accountId}/transactions/credit': """const b = pm.response.json();
if (b.success) pm.collectionVariables.set('transactionId', b.data.id);""",
    'post /api/v1/accounts/{accountId}/transactions/debit': """const b = pm.response.json();
if (b.success) pm.collectionVariables.set('transactionId', b.data.id);""",
    'post /api/v1/transfers': """const b = pm.response.json();
if (b.success) pm.collectionVariables.set('transferId', b.data.id);""",
}

PATH_VAR_DEFAULT = {
    'accountId': '{{accountId}}',
    'transactionId': '{{transactionId}}',
    'transferId': '{{transferId}}',
}

PUBLIC = {'/api/v1/auth/register', '/api/v1/auth/login', '/api/v1/auth/refresh'}

# Bodies the generated examples cannot get right: they must point at ids the run
# has actually captured, and the amounts are sized so a straight top-to-bottom
# run never trips INSUFFICIENT_BALANCE.
BODY_OVERRIDE = {
    # Email is globally unique and phone is too, so the generated examples would
    # let the collection run exactly once. A fresh identity is minted per run
    # instead, and phone is left out entirely - it is optional, and a hard-coded
    # one in a shared collection is a landmine the second person to run it hits.
    'post /api/v1/auth/register': {
        'firstName': 'Piyush', 'lastName': 'Priyadarshi',
        'email': '{{email}}', 'password': '{{password}}',
    },
    'post /api/v1/auth/login': {'email': '{{email}}', 'password': '{{password}}'},
    'post /api/v1/auth/refresh': {'refreshToken': '{{refreshToken}}'},
    'put /api/v1/users/me': {'firstName': 'Piyush', 'lastName': 'Priyadarshi'},
    'post /api/v1/transfers': {
        'sourceAccountId': '{{accountId}}',
        'destinationAccountId': '{{secondAccountId}}',
        'amount': '1000.00',
        'description': 'Monthly transfer',
    },
    'post /api/v1/transfers/bank-to-cash': {
        'accountId': '{{accountId}}', 'amount': '500.00', 'description': 'ATM withdrawal',
    },
    'post /api/v1/transfers/cash-to-bank': {
        'accountId': '{{accountId}}', 'amount': '200.00', 'description': 'Cash deposit',
    },
    # ACTIVE is a safe no-op. CLOSED would shut the account before the
    # transaction requests below it ever ran.
    'patch /api/v1/accounts/{accountId}/status': {'status': 'ACTIVE'},
    'post /api/v1/accounts/{accountId}/transactions/credit': {
        'amount': '5000.00', 'category': 'SALARY', 'description': 'Monthly salary',
    },
    'post /api/v1/accounts/{accountId}/transactions/debit': {
        'amount': '3000.00', 'category': 'SHOPPING', 'description': 'Laptop purchase',
        'merchant': 'Amazon',
        'metadata': {'paymentMethod': 'UPI', 'referenceNumber': 'UPI123456'},
    },
    'post /api/v1/cash/deposit': {'amount': '2000.00', 'description': 'Cash received'},
    'post /api/v1/cash/withdraw': {'amount': '1500.00', 'description': 'Groceries'},
}

# ------------------------------------------------------------------ build items

def build_item(path, method, op):
    key = f'{method} {path}'
    # OpenAPI writes {accountId}; Postman substitutes :accountId. Without this
    # the segment stays literal and every path-variable request 404s.
    postman_path = re.sub(r'\{([^}]+)\}', r':\1', path)
    segments = [s for s in postman_path.strip('/').split('/') if s]
    url = {
        'raw': '{{baseUrl}}' + postman_path,
        'host': ['{{baseUrl}}'],
        'path': segments,
    }

    variables, query = [], []
    for p in op.get('parameters', []):
        if p['in'] == 'path':
            variables.append({
                'key': p['name'],
                'value': PATH_VAR_DEFAULT.get(p['name'], ''),
                'description': p.get('description') or f"{p['name']} of the resource",
            })
        elif p['in'] == 'query':
            query.append({'key': p['name'], 'value': '', 'disabled': True,
                          'description': p.get('description', '')})
    if variables:
        url['variable'] = variables

    # List endpoints take paging even though springdoc folds Pageable into a ref.
    if method == 'get' and RESPONSE_DATA.get(key, (None, False))[1]:
        query = [
            {'key': 'page', 'value': '0', 'description': 'Zero-based page index'},
            {'key': 'size', 'value': '20', 'description': 'Page size'},
            {'key': 'sort', 'value': 'transactionDate,desc',
             'description': 'field,asc|desc'},
        ]
    if query:
        url['query'] = query
        url['raw'] = url['raw'] + '?' + '&'.join(
            f"{q['key']}={q['value']}" for q in query if not q.get('disabled'))

    headers = []
    if op.get('requestBody'):
        headers.append({'key': 'Content-Type', 'value': 'application/json'})
    needs_key = any(p.get('name') == 'Idempotency-Key' for p in op.get('parameters', []))
    if needs_key:
        headers.append({
            'key': 'Idempotency-Key',
            'value': '{{$guid}}',
            'description': 'Required. A fresh GUID per operation. Reuse the SAME '
                           'value to retry safely - the original result is returned '
                           'instead of moving money twice.',
        })

    request = {
        'method': method.upper(),
        'header': headers,
        'url': url,
        'description': (op.get('description') or op.get('summary') or ''),
    }
    if path in PUBLIC:
        request['auth'] = {'type': 'noauth'}

    body = BODY_OVERRIDE.get(key, request_example(op))
    if body is not None:
        request['body'] = {
            'mode': 'raw',
            'raw': json.dumps(body, indent=2),
            'options': {'raw': {'language': 'json'}},
        }

    item = {'name': op.get('summary') or key, 'request': request, 'response': []}

    if key == 'post /api/v1/auth/register':
        item.setdefault('event', []).append({'listen': 'prerequest', 'script': {
            'type': 'text/javascript', 'exec': [
                "// A fresh identity per run, so this collection can be re-run",
                "// indefinitely: email is globally unique on the server.",
                "pm.collectionVariables.set('email', `accountflow.${Date.now()}@example.com`);",
                "pm.collectionVariables.set('password', 'Secret123');",
            ]}})

    if key in CAPTURE:
        # append, not assign: register already carries a pre-request script.
        item.setdefault('event', []).append({'listen': 'test',
                          'script': {'type': 'text/javascript',
                                     'exec': CAPTURE[key].split('\n')}})

    # A saved example response, so the shape is visible without calling the API.
    name, paged = RESPONSE_DATA.get(key, (None, False))
    if name:
        data = resolve({'$ref': f'#/components/schemas/{name}'})
        if paged:
            data = [data]
        code = 201 if (method == 'post' and '201' in op.get('responses', {})) else 200
        item['response'].append({
            'name': 'Success',
            'originalRequest': {k: v for k, v in request.items() if k != 'description'},
            'status': 'Created' if code == 201 else 'OK',
            'code': code,
            '_postman_previewlanguage': 'json',
            'header': [{'key': 'Content-Type', 'value': 'application/json'},
                       {'key': 'X-Request-Id',
                        'value': '0f9c1a52-8f1e-4a7b-9a3d-2c6b5e4d1a88'}],
            'body': json.dumps(envelope(data, paged is True,
                                        op.get('summary')), indent=2),
        })

    # One representative failure per operation, taken from the spec itself.
    for status in ('422', '404', '401', '400'):
        resp = op.get('responses', {}).get(status)
        if not resp or 'content' not in resp:
            continue
        # Explicitly annotated responses may carry no JSON body or a */* content
        # type; only the customizer-generated ones have a usable example.
        media = resp['content'].get('application/json')
        if not media:
            continue
        ex = media.get('examples', {}).get('default', {})
        if not ex:
            continue
        item['response'].append({
            'name': f'Error {status}',
            'originalRequest': {k: v for k, v in request.items() if k != 'description'},
            'status': status, 'code': int(status),
            '_postman_previewlanguage': 'json',
            'header': [{'key': 'Content-Type', 'value': 'application/json'}],
            'body': json.dumps(ex['value'], indent=2),
        })
        break
    return item


def second_account_request(create_item):
    """A transfer needs a destination, so the run creates two accounts."""
    import copy
    item = copy.deepcopy(create_item)
    item['name'] = 'Create a second account (transfer destination)'
    item['request']['body']['raw'] = json.dumps({
        'accountName': 'SBI Savings', 'bankName': 'SBI', 'accountType': 'SAVINGS',
        'currency': 'INR', 'accountNumber': '20100987654321',
        'openingBalance': '20000.00', 'creditLimit': '0.00',
    }, indent=2)
    item['event'] = [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': [
        "const b = pm.response.json();",
        "if (b.success) pm.collectionVariables.set('secondAccountId', b.data.id);",
        "pm.test('second account created', () => pm.response.to.have.status(201));",
    ]}}]
    item['response'] = []
    return item


ORDER = ['Health', 'Authentication', 'Users', 'Accounts', 'Transactions', 'Transfers', 'Cash']
folders = collections.OrderedDict((t, []) for t in ORDER)

for path, methods in SPEC['paths'].items():
    for method, op in methods.items():
        tag = (op.get('tags') or ['Other'])[0]
        folders.setdefault(tag, []).append((path, method, op))

# Register first, then read-only calls, so Runner order is a usable happy path.
PRIORITY = {'post': 0, 'get': 1, 'put': 2, 'patch': 3, 'delete': 4}
items = []
deferred = []
for tag, ops in folders.items():
    if not ops:
        continue
    AUTH_FIRST = {'/api/v1/auth/register': 0, '/api/v1/auth/login': 1, '/api/v1/auth/refresh': 2}
    ops.sort(key=lambda o: (AUTH_FIRST.get(o[0], 9), PRIORITY.get(o[1], 9), o[0]))
    built = [build_item(p, m, o) for p, m, o in ops]
    # Logout revokes every token, so it must not run before the rest of a
    # collection run. Held back and appended at the very end.
    held = [i for i in built if i['request']['url']['raw'].endswith('/auth/logout')]
    built = [i for i in built if i not in held]
    deferred.extend(held)
    if tag == 'Accounts':
        built.insert(1, second_account_request(built[0]))
    items.append({
        'name': tag,
        'description': next((t['description'] for t in SPEC.get('tags', [])
                             if t['name'] == tag), ''),
        'item': built,
    })

if deferred:
    for item in deferred:
        item['name'] = item['name'] + ' (run last - revokes every token)'
    items.append({
        'name': 'Session end',
        'description': 'Run last: logging out revokes all refresh tokens and '
                       'invalidates outstanding access tokens.',
        'item': deferred,
    })

collection = {
    'info': {
        'name': 'AccountFlow API',
        '_postman_id': 'a1c0f10w-0000-4000-8000-accountflow01',
        'description': SPEC['info']['description'] + """

---

## Using this collection

1. Import this file and `AccountFlow.postman_environment.json`.
2. Set `baseUrl` (default `http://localhost:8082`).
3. Send **Authentication → Register a new user**. The test script stores
   `accessToken`, `refreshToken` and `userId` automatically, and every other
   request inherits the bearer token, so nothing needs pasting by hand.
4. Send **Accounts → Create an account**; `accountId` is captured the same way.
5. Everything else is then runnable in order.

Captured automatically: `accessToken`, `refreshToken`, `userId`, `accountId`,
`transactionId`, `transferId`.

`Idempotency-Key` is set to `{{$guid}}`, so each send is a new operation. To
test a retry, replace it with a fixed string and send twice: the second call
returns the first result and moves no money.""",
        'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    },
    'auth': {'type': 'bearer',
             'bearer': [{'key': 'token', 'value': '{{accessToken}}', 'type': 'string'}]},
    'variable': [
        {'key': 'baseUrl', 'value': 'https://accountflow-2eff.onrender.com'},
        {'key': 'email', 'value': ''},
        {'key': 'password', 'value': 'Secret123'},
        {'key': 'accessToken', 'value': ''},
        {'key': 'refreshToken', 'value': ''},
        {'key': 'userId', 'value': ''},
        {'key': 'accountId', 'value': ''},
        {'key': 'secondAccountId', 'value': ''},
        {'key': 'transactionId', 'value': ''},
        {'key': 'transferId', 'value': ''},
    ],
    'item': items,
}

OUT = '/Users/piyushpriyadarshi/Documents/CMS/AccountFlow/postman'
with open(f'{OUT}/AccountFlow.postman_collection.json', 'w') as f:
    json.dump(collection, f, indent=2)

environment = {
    'name': 'AccountFlow - Local',
    'values': [
        {'key': 'baseUrl', 'value': 'https://accountflow-2eff.onrender.com', 'enabled': True},
        {'key': 'email', 'value': '', 'enabled': True},
        {'key': 'password', 'value': 'Secret123', 'enabled': True},
        {'key': 'accessToken', 'value': '', 'enabled': True},
        {'key': 'refreshToken', 'value': '', 'enabled': True},
        {'key': 'userId', 'value': '', 'enabled': True},
        {'key': 'accountId', 'value': '', 'enabled': True},
        {'key': 'secondAccountId', 'value': '', 'enabled': True},
        {'key': 'transactionId', 'value': '', 'enabled': True},
        {'key': 'transferId', 'value': '', 'enabled': True},
    ],
    '_postman_variable_scope': 'environment',
}
with open(f'{OUT}/AccountFlow.postman_environment.json', 'w') as f:
    json.dump(environment, f, indent=2)

print(f'folders: {len(items)}')
for folder in items:
    print(f"  {folder['name']:<16} {len(folder['item'])} requests")
print('total requests:', sum(len(f['item']) for f in items))
