Component({
  properties: {
    service: { type: Object, value: null },
    compact: { type: Boolean, value: false },
    index: { type: Number, value: 0 }
  },
  data: { imageFailed: false },
  observers: { 'service.coverUrl': function () { this.setData({ imageFailed: false }) } },
  methods: {
    imageError() { this.setData({ imageFailed: true }) },
    handleTap() {
      this.triggerEvent('select', { serviceId: String(this.data.service.id) })
    },
    handleBook() {
      this.triggerEvent('book', { serviceId: String(this.data.service.id) })
    }
  }
})
